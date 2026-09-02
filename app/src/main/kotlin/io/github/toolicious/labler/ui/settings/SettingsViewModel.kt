package io.github.toolicious.labler.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.ble.FoundPrinter
import io.github.toolicious.labler.ble.PrinterScanner
import io.github.toolicious.labler.ble.PrinterState
import io.github.toolicious.labler.printer.PhomemoProtocol
import io.github.toolicious.labler.printer.PrinterFamily
import io.github.toolicious.labler.printer.PrinterProtocols
import io.github.toolicious.labler.printer.ProtocolTuning
import io.github.toolicious.labler.printer.TestPattern
import io.github.toolicious.labler.printer.Tunable

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

    /** Experimental 0x1F print density: 0 = off, 1..15 = darkness. Applied on the next print. */
    val printDensity = container.settings.printDensity
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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
        else list.filter { f -> PrinterProtocols.matchName(f.name) != null }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The family whose calibration is on offer: the connected printer, or failing that the
     * remembered one. Null where that family has nothing left to pin down, which hides the
     * whole section.
     */
    val calibrationFamily = combine(printerState, savedPrinter) { state, saved ->
        val family = (state as? PrinterState.Ready)?.family ?: saved?.family
        family?.takeIf { PrinterProtocols.baseOf(it).tunables.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Values stored for that family, empty where the protocol's own are in use. */
    val calibration = combine(container.settings.protocolTuning, calibrationFamily) { tuning, family ->
        family?.let { tuning[it] } ?: ProtocolTuning.NONE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProtocolTuning.NONE)

    fun setCalibration(tunable: Tunable, value: String?) {
        val family = calibrationFamily.value ?: return
        viewModelScope.launch { container.settings.saveTuning(family, tunable, value) }
    }

    /**
     * Works the feed resolution out of a measured distance. The app knows how many dots the
     * test pattern puts between its first and last tick, the user supplies what the ruler
     * showed. The measurement is kept alongside so the settings screen can show what was
     * entered rather than only what was made of it.
     */
    fun calibrateFromMeasurement(measuredMm: Float) {
        if (measuredMm <= 0f) return
        val family = calibrationFamily.value ?: return
        val span = TestPattern.calibrationSpanDots()
        setCalibration(Tunable.DOTS_PER_MM, (span / measuredMm).toString())
        viewModelScope.launch {
            container.settings.saveCalibrationMeasurement(family, measuredMm.toString())
        }
    }

    /** What the user last typed into the calibration dialog, for showing it back to them. */
    val calibrationMeasurement =
        combine(container.settings.calibrationMeasurements, calibrationFamily) { measured, family ->
            family?.let { measured[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Drops only the length calibration, leaving anything else a family offers alone. */
    fun resetLengthCalibration() {
        val family = calibrationFamily.value ?: return
        setCalibration(Tunable.DOTS_PER_MM, null)
        viewModelScope.launch { container.settings.saveCalibrationMeasurement(family, null) }
    }

    fun resetCalibration() {
        val family = calibrationFamily.value ?: return
        viewModelScope.launch {
            container.settings.clearTuning(family)
            container.settings.saveCalibrationMeasurement(family, null)
        }
    }

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
        // "Show all" can offer a device no family claims. Connecting to one anyway is the
        // user overriding the filter, and it gets the family the app has always assumed.
        val family = PrinterProtocols.matchName(found.name)?.family ?: PrinterFamily.DEFAULT
        viewModelScope.launch {
            runCatching { manager.connect(found.device, found.name, family) }
        }
    }

    fun reconnectSaved() = manager.connectSavedActive()

    fun disconnect() = manager.disconnect()

    fun forget() {
        viewModelScope.launch { manager.forget() }
    }

    private val _commandFeedback = MutableStateFlow<String?>(null)
    val commandFeedback = _commandFeedback.asStateFlow()

    /** Experimental: teach the gap detection. A Phomemo command, so only sent to one. */
    fun learnGap() {
        val connected = (manager.state.value as? PrinterState.Ready)?.family
        if (connected != PrinterFamily.PHOMEMO) return
        viewModelScope.launch {
            _commandFeedback.value = runCatching {
                manager.sendCommand(PhomemoProtocol.LEARN_GAP)
            }.fold(
                onSuccess = { "Teach command sent. The printer may feed a few labels." },
                onFailure = { "Error: ${it.message}" },
            )
        }
    }

    /** Experimental 0x1F print density (0 = off, 1..15 = darkness). Takes effect on the next print. */
    fun setPrintDensity(level: Int) {
        viewModelScope.launch { container.settings.savePrintDensity(level) }
    }

    override fun onCleared() {
        stopScan()
    }
}
