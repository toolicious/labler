package io.github.toolicious.labler.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import io.github.toolicious.labler.R
import io.github.toolicious.labler.printer.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class FoundPrinter(val device: BluetoothDevice, val name: String, val rssi: Int)

@SuppressLint("MissingPermission") // Permissions are ensured before every action in the UI
class PrinterScanner(private val context: Context) {

    /** Raw BLE scan as a Flow; only devices with a name are reported. */
    fun scan(): Flow<FoundPrinter> = callbackFlow {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
            ?: run { close(IllegalStateException(context.getString(R.string.err_no_bluetooth))); return@callbackFlow }
        if (!adapter.isEnabled) {
            close(IllegalStateException(context.getString(R.string.err_bt_off)))
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner
            ?: run { close(IllegalStateException(context.getString(R.string.err_no_scanner))); return@callbackFlow }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device?.name ?: return
                trySend(FoundPrinter(result.device, name, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed (code $errorCode)"))
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, cb)
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }

    /** Returns the first printer with a matching name prefix, or null after a timeout. */
    suspend fun findFirstPrinter(timeoutMs: Long = 15_000): FoundPrinter? = withTimeoutOrNull(timeoutMs) {
        scan().first { found -> Protocol.DEVICE_NAME_PREFIXES.any { found.name.startsWith(it) } }
    }
}
