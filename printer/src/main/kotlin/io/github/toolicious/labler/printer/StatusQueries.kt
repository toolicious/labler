package io.github.toolicious.labler.printer

/**
 * Commands a family answers with device status, written to the write characteristic and answered
 * as a notification. Null on [PrinterProtocol.statusQueries] for a family that cannot be asked.
 *
 * Not a data class: the byte arrays would give it an identity comparison that means nothing.
 */
class StatusQueries(
    val battery: ByteArray,
    val model: ByteArray,
    val firmware: ByteArray,
    val serial: ByteArray,
    val hardware: ByteArray,
)
