package io.github.toolicious.labler.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.github.toolicious.labler.R
import io.github.toolicious.labler.printer.PrinterProtocol
import io.github.toolicious.labler.printer.TransportProfile

/** Established connection to a printer including its protocol and the negotiated chunk size. */
class PrinterConnection private constructor(
    val client: GattClient,
    val protocol: PrinterProtocol,
    val uuids: PrinterUuids,
    val writeChar: BluetoothGattCharacteristic,
    val chunkSize: Int,
    val mtu: Int,
) {
    fun close() = client.close()

    companion object {
        suspend fun open(
            context: Context,
            device: BluetoothDevice,
            protocol: PrinterProtocol,
            autoConnect: Boolean = false,
            connectTimeoutMs: Long? = 10_000,
            log: (String) -> Unit = {},
        ): PrinterConnection {
            val client = GattClient()
            val uuids = PrinterUuids.of(protocol.ble)
            try {
                client.connect(context, device, autoConnect, connectTimeoutMs)
                log("Connected, discovering services ...")
                client.discoverServices()
                val writeChar = client.findCharacteristic(uuids.service, uuids.write)
                    ?: error(
                        "Print characteristic not found. " +
                            "Is this a ${protocol.ble.namePrefixes.joinToString("/")}?"
                    )
                val transport = protocol.transport
                // A phone answers the request with whatever it and the printer agree on, so
                // the chunk follows the packet that came back rather than the one asked for.
                val mtu = client.requestMtu(transport.requestedMtu)
                val usable = mtu - TransportProfile.ATT_OVERHEAD
                if (usable < transport.minChunkSize) {
                    error(
                        context.getString(
                            R.string.err_mtu_too_small,
                            transport.minChunkSize + TransportProfile.ATT_OVERHEAD,
                        )
                    )
                }
                val chunkSize = minOf(transport.chunkSize, usable)
                log("MTU $mtu, chunk size $chunkSize bytes")
                return PrinterConnection(client, protocol, uuids, writeChar, chunkSize, mtu)
            } catch (t: Throwable) {
                client.close()
                throw t
            }
        }
    }
}
