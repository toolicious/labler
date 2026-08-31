package io.github.toolicious.labler.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.github.toolicious.labler.printer.PrinterProtocol

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
                val mtu = client.requestMtu(transport.requestedMtu)
                val chunkSize = if (mtu >= transport.minMtuForFullChunks) {
                    transport.chunkSize
                } else {
                    transport.fallbackChunkSize
                }
                log("MTU $mtu, chunk size $chunkSize bytes")
                return PrinterConnection(client, protocol, uuids, writeChar, chunkSize, mtu)
            } catch (t: Throwable) {
                client.close()
                throw t
            }
        }
    }
}
