package io.github.toolicious.labler.ui.print

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.model.LengthMode
import io.github.toolicious.labler.model.Placeholders
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.render.LabelRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TemplatePrintViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val manager = container.printerManager
    private val templateRepo = container.templateRepository
    private val historyRepo = container.historyRepository

    val printerState = manager.state
    val savedPrinter = container.settings.savedPrinter

    fun connect() = manager.connectSavedActive()

    fun cancelConnect() = manager.cancelConnect()

    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    /**
     * [placeholders] carries the date, time and answers as they were the moment the user pressed
     * print; only the counter moves on from copy to copy.
     */
    fun print(template: LabelTemplate, media: MediaType, copies: Int, placeholders: Placeholders.Context) {
        if (_working.value) return
        _working.value = true
        _error.value = null
        _done.value = false
        viewModelScope.launch {
            try {
                val hasCounter = Placeholders.containsCounter(template.elements)
                val resolvedPerCopy = List(copies) { index ->
                    Placeholders.resolve(
                        template.elements,
                        placeholders.copy(counter = template.counterValue + index)
                    )
                }
                val reanchored = resolvedPerCopy.map { LabelRenderer.reanchor(template.elements, it) }
                // renderMono derives the length from these resolved elements, so on an auto-length
                // tape each copy gets exactly the length its own content needs. A counter rolling
                // from 9 to 10 therefore makes that one copy longer, not all of them.
                val images = reanchored.map { LabelRenderer.renderMono(template.spec, it) }

                manager.printJobs(images, media)

                if (hasCounter) {
                    templateRepo.setCounter(template.id, template.counterValue + copies)
                }
                // Counted here rather than derived from the history, which only keeps the last
                // 50 entries. Both the overview and the editor print through this sheet, so this
                // is the one place a template print passes.
                templateRepo.addPrints(template.id, copies)
                val printedSpec = template.spec.copy(media = media)
                val printed = reanchored.first()
                // A history entry is a snapshot of what actually came out: reprinting it has to
                // reproduce that label rather than recompute one. So it stores the printed length
                // instead of the minimum and pins the mode to FIXED. The shift the other two modes
                // draw their content with has to be baked into the coordinates for the same reason,
                // because a fixed label draws them where they stand and would otherwise lose the
                // blank tape that was in front of the content.
                val printedOffset = LabelRenderer.contentOffsetPx(printedSpec, printed)
                historyRepo.record(
                    templateId = template.id,
                    templateName = template.name,
                    spec = printedSpec
                        .copy(lengthMm = LabelRenderer.effectiveLengthMm(printedSpec, printed))
                        .withLengthMode(LengthMode.FIXED),
                    resolvedElements = printed.map { it.moved(printedOffset, 0f) },
                    copies = copies,
                )
                _done.value = true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // A GATT timeout carries no message so that this localized text is used.
                _error.value = t.message ?: getApplication<Application>().getString(R.string.err_print_failed)
            } finally {
                _working.value = false
            }
        }
    }

    fun reset() {
        _error.value = null
        _done.value = false
    }
}
