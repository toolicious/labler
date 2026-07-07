package io.github.toolicious.labler.ui.print

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.model.Placeholders
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.render.LabelRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun print(template: LabelTemplate, media: MediaType, copies: Int, answers: Map<String, String>) {
        if (_working.value) return
        _working.value = true
        _error.value = null
        _done.value = false
        viewModelScope.launch {
            try {
                val hasCounter = Placeholders.containsCounter(template.elements)
                val now = Date()
                val dateText = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(now)
                val timeText = SimpleDateFormat("HH:mm", Locale.GERMANY).format(now)

                val resolvedPerCopy = List(copies) { index ->
                    Placeholders.resolve(
                        template.elements,
                        Placeholders.Context(
                            dateText = dateText,
                            timeText = timeText,
                            counter = template.counterValue + index,
                            answers = answers,
                        )
                    )
                }
                val reanchored = resolvedPerCopy.map { LabelRenderer.reanchor(template.elements, it) }
                val images = reanchored.map { LabelRenderer.renderMono(template.spec, it) }

                manager.printJobs(images, media)

                if (hasCounter) {
                    templateRepo.setCounter(template.id, template.counterValue + copies)
                }
                historyRepo.record(
                    templateId = template.id,
                    templateName = template.name,
                    spec = template.spec.copy(media = media),
                    resolvedElements = reanchored.first(),
                    copies = copies,
                )
                _done.value = true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _error.value = t.message ?: "Print failed"
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
