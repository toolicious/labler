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
import io.github.toolicious.labler.printer.PrintResult
import io.github.toolicious.labler.printer.PrinterFamily
import io.github.toolicious.labler.printer.PrinterProtocols
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
                connectInternal(
                    device, saved.name, saved.family,
                    autoConnect = true, connectTimeoutMs = null, retries = 1,
                )
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
            runCatching { connect(adapter.getRemoteDevice(saved.address), saved.name, saved.family) }
        }
    }

    /** Cancels an ongoing active connection attempt. */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        if (_state.value is PrinterState.Connecting) _state.value = PrinterState.Disconnected
    }

    /** Connects actively with a short timeout per attempt and 3 attempts; throws on failure. */
    suspend fun connect(device: BluetoothDevice, name: String, family: PrinterFamily) {
        reconnectJob?.cancelAndJoin()
        connectInternal(device, name, family, autoConnect = false, connectTimeoutMs = 6_000, retries = 3)
    }

    private suspend fun connectInternal(
        device: BluetoothDevice,
        name: String,
        family: PrinterFamily,
        autoConnect: Boolean,
        connectTimeoutMs: Long?,
        retries: Int,
    ) {
        val protocol = PrinterProtocols.of(family)
        var lastError: Throwable? = null
        for (attempt in 1..retries) {
            if (!autoConnect) _state.value = PrinterState.Connecting(attempt)
            try {
                val conn = PrinterConnection.open(
                    context, device, protocol, autoConnect, connectTimeoutMs, log = ::bleLog,
                )
                _state.value = PrinterState.Connecting(attempt)
                connection = conn
                // Built for anything the printer might say, whether that is an answer to a
                // query or an unprompted verdict on a finished job.
                statusClient = StatusClient(conn.client, conn.uuids, protocol)
                    .takeIf { it.initialize() }
                settings.savePrinter(device.address, name, family)
                bleLog("printer connected and ready: $name")
                _state.value = PrinterState.Ready(name, device.address, null, family)
                watchDisconnect(conn)
                if (protocol.statusQueries != null) {
                    startBatteryPolling()
                    fetchStatusOnce()
                }
                return
            } catch (c: CancellationException) {
                connection?.close()
                connection = null
                // Do not leave a canceled active attempt stuck as "Connecting".
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
            // connect() canceled the background reconnect before trying actively, so restore it
            // after a failure. Otherwise one failed tap leaves the app deaf to the printer
            // returning. Cannot recurse: the retry it starts runs with autoConnect = true.
            startBackgroundReconnect()
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
            val protocol = conn.protocol
            // Checked before the try, so a label meant for another printer reports the mismatch
            // instead of tearing down a perfectly good connection.
            if (images.any { it.height != protocol.geometry.headDots }) {
                error(context.getString(R.string.err_printer_mismatch))
            }
            if (media !in protocol.supportedMedia) {
                error(context.getString(R.string.err_media_unsupported))
            }
            try {
                // Experimental 0x1F darkness: 0 (off) keeps the default print path byte-for-byte.
                val density = settings.printDensity.first().takeIf { it in 1..15 }
                val payloads = images.map { protocol.buildJob(it, media, density) }
                bleLog(
                    "printing ${payloads.size} job(s) of ${payloads.first().size} bytes " +
                        "as ${protocol.framePayload(payloads.first(), conn.chunkSize).size} writes"
                )
                _state.value = PrinterState.Printing(0f, 1, payloads.size)
                gattExclusive.withLock {
                    val send: suspend () -> Unit = {
                        PrintJobSender.sendAll(conn, payloads) { progress, jobIndex ->
                            _state.value = PrinterState.Printing(progress, jobIndex, payloads.size)
                        }
                    }
                    val listener = statusClient.takeIf { protocol.awaitsPrintResult }
                    if (listener != null) {
                        val result = listener.awaitPrintResult(PRINT_RESULT_TIMEOUT_MS, send)
                        bleLog("print result: " + (result?.name ?: "none within $PRINT_RESULT_TIMEOUT_MS ms"))
                        if (result != null && !result.printed) error(printResultMessage(result))
                    } else {
                        send()
                    }
                }
                _state.value = ready.copy(batteryPercent = lastBattery ?: ready.batteryPercent)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                disconnectInternal()
                showTransientError(t.message ?: context.getString(R.string.err_print_failed))
                // Arm the reconnect here rather than leaving it to watchDisconnect: tearing the
                // connection down above clears `connection`, so the watcher's identity check no
                // longer matches (and gatt.close() usually suppresses its callback anyway).
                // Without this the printer coming back on is only noticed on the next resume.
                startBackgroundReconnect()
                throw t
            }
        }
        job.await()
    }

    /** What the printer reported back, as something to put in front of the user. */
    private fun printResultMessage(result: PrintResult): String = context.getString(
        when (result) {
            PrintResult.NO_CASSETTE -> R.string.err_no_cassette
            PrintResult.LOW_BATTERY -> R.string.err_battery_too_low
            PrintResult.CANCELLED -> R.string.err_print_canceled
            else -> R.string.err_print_failed
        }
    )

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
            // Printing counts as well: losing the link mid-job used to fall through here and
            // leave the state stuck on "Printing" forever, because the job itself was already
            // dead. The job's own error handling still runs and shows the message.
            val st = _state.value
            if (connection === conn && (st is PrinterState.Ready || st is PrinterState.Printing)) {
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

    private companion object {
        /**
         * How long to wait for a printer that reports back. It only answers once the tape is
         * through, and a long label takes its time, so this is generous; running out is treated
         * as a print that went fine rather than as a failure.
         */
        const val PRINT_RESULT_TIMEOUT_MS = 60_000L
    }
}
