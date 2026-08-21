package io.github.toolicious.labler.ui.print

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.LabelTemplate
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
                val printedSpec = template.spec.copy(media = media)
                historyRepo.record(
                    templateId = template.id,
                    templateName = template.name,
                    // A history entry is a snapshot of what actually came out, so it stores the
                    // printed length instead of the minimum and drops the auto flag: reprinting it
                    // must reproduce that label, not recompute a new length.
                    spec = printedSpec.copy(
                        lengthMm = LabelRenderer.effectiveLengthMm(printedSpec, reanchored.first()),
                        autoLength = false,
                    ),
                    resolvedElements = reanchored.first(),
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
