package io.github.toolicious.labler.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.SettingsRepository
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.printer.PrintJobBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-wide connection and print manager. Holds exactly one printer connection,
 * connects automatically to the remembered device and runs print jobs in the app scope
 * so that closing a screen does not abort the print.
 */
@SuppressLint("MissingPermission")
class PrinterManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PrinterState>(PrinterState.Disconnected)
    val state = _state.asStateFlow()

    private val _printerInfo = MutableStateFlow<PrinterInfo?>(null)
    val printerInfo = _printerInfo.asStateFlow()

    private var connection: PrinterConnection? = null
    private var statusClient: StatusClient? = null
    private var batteryJob: Job? = null
    @Volatile
    private var reconnectJob: Job? = null
    private var connectJob: Job? = null
    private var statusJob: Job? = null

    // Status queries and print jobs both write to FF02 and must not interleave.
    private val gattExclusive = Mutex()

    // Last read battery value, kept across status changes (revert after print).
    @Volatile
    private var lastBattery: Int? = null

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /**
     * Power-saving background reconnect: keeps an open request to the remembered
     * printer via connectGatt(autoConnect=true). The system connects automatically
     * as soon as the printer is back in range and switched on, without any scan.
     * Started at app launch and after an unexpected connection loss.
     */
    // @Synchronized so a resume (main thread) and an unexpected-loss re-arm (app scope) cannot both
    // launch a reconnect and orphan a GattClient. A fresh call always cancels and replaces any prior
    // job, so a stale autoConnect (e.g. after a Bluetooth off/on) is recovered rather than left stuck.
    @Synchronized
    fun startBackgroundReconnect() {
        val s = _state.value
        if (s is PrinterState.Ready || s is PrinterState.Connecting || s is PrinterState.Printing) return
        if (!BlePermissions.allGranted(context)) {
            bleLog("reconnect skipped: BLE permission missing")
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val saved = settings.savedPrinter.first() ?: run {
                bleLog("reconnect skipped: no saved printer")
                return@launch
            }
            val adapter = adapter() ?: return@launch
            if (!adapter.isEnabled) {
                bleLog("reconnect skipped: bluetooth off")
                return@launch
            }
            bleLog("arming background reconnect for ${saved.name} / ${saved.address}")
            val device = adapter.getRemoteDevice(saved.address)
            runCatching {
                connectInternal(device, saved.name, autoConnect = true, connectTimeoutMs = null, retries = 1)
            }.onFailure {
                bleLog("background reconnect ended: ${it.message}")
            }
        }
    }

    /** Active connection attempt to the remembered printer (runs in the app scope). */
    fun connectSavedActive() {
        if (_state.value is PrinterState.Ready) return
        connectJob?.cancel()
        connectJob = scope.launch {
            val saved = settings.savedPrinter.first() ?: return@launch
            val adapter = adapter()
            if (adapter == null || !adapter.isEnabled) {
                showTransientError(context.getString(R.string.err_bt_off))
                return@launch
            }
            runCatching { connect(adapter.getRemoteDevice(saved.address), saved.name) }
        }
    }

    /** Cancels an ongoing active connection attempt. */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        if (_state.value is PrinterState.Connecting) _state.value = PrinterState.Disconnected
    }

    /** Connects actively with a short timeout per attempt and 3 attempts; throws on failure. */
    suspend fun connect(device: BluetoothDevice, name: String) {
        reconnectJob?.cancelAndJoin()
        connectInternal(device, name, autoConnect = false, connectTimeoutMs = 6_000, retries = 3)
    }

    private suspend fun connectInternal(
        device: BluetoothDevice,
        name: String,
        autoConnect: Boolean,
        connectTimeoutMs: Long?,
        retries: Int,
    ) {
        var lastError: Throwable? = null
        for (attempt in 1..retries) {
            if (!autoConnect) _state.value = PrinterState.Connecting(attempt)
            try {
                val conn = PrinterConnection.open(context, device, autoConnect, connectTimeoutMs)
                _state.value = PrinterState.Connecting(attempt)
                connection = conn
                val sc = StatusClient(conn.client)
                statusClient = if (sc.initialize()) sc else null
                settings.savePrinter(device.address, name)
                bleLog("printer connected and ready: $name")
                _state.value = PrinterState.Ready(name, device.address, null)
                watchDisconnect(conn)
                startBatteryPolling()
                fetchStatusOnce()
                return
            } catch (c: CancellationException) {
                connection?.close()
                connection = null
                // Do not leave a cancelled active attempt stuck as "Connecting".
                if (!autoConnect && _state.value is PrinterState.Connecting) {
                    _state.value = PrinterState.Disconnected
                }
                throw c
            } catch (t: Throwable) {
                lastError = t
                connection?.close()
                connection = null
                if (attempt < retries) delay(600L * attempt)
            }
        }
        if (!autoConnect) {
            showTransientError(lastError?.message ?: context.getString(R.string.err_connect_failed))
        }
        throw lastError ?: IllegalStateException(context.getString(R.string.err_connect_failed))
    }

    /** Disconnects on user request (no automatic reconnect afterwards). */
    fun disconnect() {
        reconnectJob?.cancel()
        disconnectInternal()
        _state.value = PrinterState.Disconnected
    }

    suspend fun forget() {
        settings.forgetPrinter()
        disconnect()
    }

    suspend fun print(image: MonoImage, media: MediaType, copies: Int) =
        printJobs(List(copies) { image }, media)

    /** Prints a list of jobs (one MonoImage per label, e.g. for serial numbers). */
    suspend fun printJobs(images: List<MonoImage>, media: MediaType) {
        val job = scope.async {
            val ready = _state.value as? PrinterState.Ready
                ?: error(context.getString(R.string.err_not_connected))
            val conn = connection ?: error(context.getString(R.string.err_not_connected))
            try {
                val payloads = images.map { PrintJobBuilder.buildJob(it, media) }
                _state.value = PrinterState.Printing(0f, 1, payloads.size)
                gattExclusive.withLock {
                    PrintJobSender.sendAll(conn, payloads) { progress, jobIndex ->
                        _state.value = PrinterState.Printing(progress, jobIndex, payloads.size)
                    }
                }
                _state.value = ready.copy(batteryPercent = lastBattery ?: ready.batteryPercent)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                disconnectInternal()
                showTransientError(t.message ?: context.getString(R.string.err_print_failed))
                throw t
            }
        }
        job.await()
    }

    /** Raw command to the print characteristic (for experimental features). */
    suspend fun sendCommand(bytes: ByteArray) {
        check(_state.value is PrinterState.Ready) { context.getString(R.string.err_not_connected) }
        val conn = connection ?: error(context.getString(R.string.err_not_connected))
        gattExclusive.withLock {
            conn.client.writeCharacteristic(conn.writeChar, bytes)
        }
    }

    private fun showTransientError(message: String) {
        val error = PrinterState.Error(message)
        _state.value = error
        scope.launch {
            delay(4_000)
            _state.compareAndSet(error, PrinterState.Disconnected)
        }
    }

    private fun watchDisconnect(conn: PrinterConnection) {
        scope.launch {
            conn.client.connectionChanges.first { it.newState == BluetoothProfile.STATE_DISCONNECTED }
            if (connection === conn && _state.value is PrinterState.Ready) {
                disconnectInternal()
                _state.value = PrinterState.Disconnected
                // Unexpected loss (printer switched off): wait for its return.
                startBackgroundReconnect()
            }
        }
    }

    private fun startBatteryPolling() {
        batteryJob?.cancel()
        batteryJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val st = _state.value
                if (st is PrinterState.Ready) {
                    val battery = runCatching { gattExclusive.withLock { statusClient?.batteryPercent() } }.getOrNull()
                    if (battery != null) {
                        lastBattery = battery
                        _state.compareAndSet(st, st.copy(batteryPercent = battery))
                    }
                }
            }
        }
    }

    /** After connecting, fetch info + battery once (asynchronous, does not block Ready). */
    private fun fetchStatusOnce() {
        statusJob?.cancel()
        statusJob = scope.launch {
            val sc = statusClient ?: return@launch
            sc.drainInitialPush()
            // Only write while sc is the active client (otherwise it was disconnected in the meantime).
            val info = runCatching { gattExclusive.withLock { sc.printerInfo() } }.getOrNull()
            if (statusClient === sc) _printerInfo.value = info
            // Right after connecting the battery sometimes briefly reports 0. Retry for up to ~8 s
            // until a plausible (>0) value arrives; if it stays 0 after that, it counts as truly empty.
            var battery: Int? = null
            var tries = 0
            while (tries < 6 && statusClient === sc) {
                val b = runCatching { gattExclusive.withLock { sc.batteryPercent() } }.getOrNull()
                if (b != null) battery = b
                if (b != null && b > 0) break
                tries++
                if (tries < 6) delay(1200)
            }
            if (battery != null) lastBattery = battery
            val st = _state.value
            if (battery != null && statusClient === sc && st is PrinterState.Ready) {
                _state.compareAndSet(st, st.copy(batteryPercent = battery))
            }
        }
    }

    private fun disconnectInternal() {
        statusJob?.cancel()
        statusJob = null
        batteryJob?.cancel()
        batteryJob = null
        connection?.close()
        connection = null
        statusClient = null
        _printerInfo.value = null
    }
}
