package io.github.toolicious.labler.ble

import io.github.toolicious.labler.printer.Protocol
import java.util.UUID

object PrinterUuids {
    val PRINT_SERVICE: UUID = UUID.fromString(Protocol.PRINT_SERVICE_UUID)
    val PRINT_WRITE: UUID = UUID.fromString(Protocol.PRINT_WRITE_CHAR_UUID)
    val PRINT_NOTIFY: UUID = UUID.fromString(Protocol.PRINT_NOTIFY_CHAR_UUID)

    /** Client Characteristic Configuration Descriptor (for notify subscription). */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
