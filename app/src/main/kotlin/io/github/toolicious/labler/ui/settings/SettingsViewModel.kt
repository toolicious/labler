package io.github.toolicious.labler.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.ble.FoundPrinter
import io.github.toolicious.labler.ble.PrinterScanner
import io.github.toolicious.labler.printer.Protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val manager = container.printerManager

    val printerState = manager.state
    val printerInfo = manager.printerInfo
    val savedPrinter = container.settings.savedPrinter
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<FoundPrinter>>(emptyList())
    private val _showAll = MutableStateFlow(false)
    val showAll = _showAll.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError = _scanError.asStateFlow()

    /** Found devices, filtered to known printer prefixes by default. */
    val visibleResults = combine(_scanResults, _showAll) { list, all ->
        if (all) list
        else list.filter { f -> Protocol.DEVICE_NAME_PREFIXES.any { f.name.startsWith(it) } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var scanJob: Job? = null

    fun setShowAll(value: Boolean) {
        _showAll.value = value
    }

    fun startScan() {
        if (_scanning.value) return
        _scanResults.value = emptyList()
        _scanError.value = null
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(15_000) {
                    PrinterScanner(getApplication()).scan().collect { found ->
                        val current = _scanResults.value
                        if (current.none { it.device.address == found.device.address }) {
                            _scanResults.value = (current + found).sortedByDescending { it.rssi }
                        }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _scanError.value = t.message ?: "Scan failed"
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun connectTo(found: FoundPrinter) {
        stopScan()
        viewModelScope.launch {
            runCatching { manager.connect(found.device, found.name) }
        }
    }

    fun reconnectSaved() = manager.connectSavedActive()

    fun disconnect() = manager.disconnect()

    fun forget() {
        viewModelScope.launch { manager.forget() }
    }

    private val _commandFeedback = MutableStateFlow<String?>(null)
    val commandFeedback = _commandFeedback.asStateFlow()

    /** Experimental: teach the gap detection. */
    fun learnGap() {
        viewModelScope.launch {
            _commandFeedback.value = runCatching {
                manager.sendCommand(Protocol.LEARN_GAP)
            }.fold(
                onSuccess = { "Teach command sent. The printer may feed a few labels." },
                onFailure = { "Error: ${it.message}" },
            )
        }
    }

    override fun onCleared() {
        stopScan()
    }
}
