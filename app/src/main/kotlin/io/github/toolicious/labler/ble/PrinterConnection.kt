package io.github.toolicious.labler.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.github.toolicious.labler.printer.Protocol

/** Established connection to the printer including the negotiated chunk size. */
class PrinterConnection private constructor(
    val client: GattClient,
    val writeChar: BluetoothGattCharacteristic,
    val chunkSize: Int,
    val mtu: Int,
) {
    fun close() = client.close()

    companion object {
        suspend fun open(
            context: Context,
            device: BluetoothDevice,
            autoConnect: Boolean = false,
            connectTimeoutMs: Long? = 10_000,
            log: (String) -> Unit = {},
        ): PrinterConnection {
            val client = GattClient()
            try {
                client.connect(context, device, autoConnect, connectTimeoutMs)
                log("Connected, discovering services ...")
                client.discoverServices()
                val writeChar = client.findCharacteristic(PrinterUuids.PRINT_SERVICE, PrinterUuids.PRINT_WRITE)
                    ?: error("Print characteristic (0xFF02) not found. Is this a P15/P12/L13?")
                val mtu = client.requestMtu(Protocol.REQUESTED_MTU)
                val chunkSize = if (mtu >= Protocol.MIN_MTU_FOR_FULL_CHUNKS) {
                    Protocol.CHUNK_SIZE
                } else {
                    Protocol.FALLBACK_CHUNK_SIZE
                }
                log("MTU $mtu, chunk size $chunkSize bytes")
                return PrinterConnection(client, writeChar, chunkSize, mtu)
            } catch (t: Throwable) {
                client.close()
                throw t
            }
        }
    }
}
