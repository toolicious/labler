package io.github.toolicious.labler.ui.print

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.MonoImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrintViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val manager = container.printerManager

    val printerState = manager.state
    val savedPrinter = container.settings.savedPrinter

    fun connect() = manager.connectSavedActive()

    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    fun print(image: MonoImage, media: MediaType, copies: Int) {
        if (_working.value) return
        _working.value = true
        _error.value = null
        _done.value = false
        viewModelScope.launch {
            try {
                manager.print(image, media, copies)
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
