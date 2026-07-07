package io.github.toolicious.labler.printer

/**
 * Byte protocol of the P15/P12/L13 label printer family ("0x10" protocol with
 * embedded ESC/POS raster command GS v 0). Clean-room implementation based on
 * documented protocol facts; verified on the device.
 */
object Protocol {

    // BLE identification (as strings, so this module stays pure JVM)
    const val PRINT_SERVICE_UUID = "0000ff00-0000-1000-8000-00805f9b34fb"
    const val PRINT_WRITE_CHAR_UUID = "0000ff02-0000-1000-8000-00805f9b34fb"
    // Status responses (battery/model/...) arrive as notifications on FF01 of the same service.
    const val PRINT_NOTIFY_CHAR_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"

    /** Name prefixes of compatible printers (name pattern "P15_xxxx_BLE" etc.). */
    val DEVICE_NAME_PREFIXES = listOf("P15", "P12", "L13")

    // Print head geometry
    const val HEAD_DOTS = 96
    const val DOTS_PER_MM = 8
    const val BYTES_PER_COLUMN = HEAD_DOTS / 8

    // Commands
    val INIT = byteArrayOf(0x10, 0xFF.toByte(), 0x40)
    val PRINT_START = byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x02)
    val RASTER_GS_V0 = byteArrayOf(0x1D, 0x76, 0x30, 0x00)
    val PRINT_END = byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x45)
    val FORM_FEED = byteArrayOf(0x1D, 0x0C)

    /** Number of zero bytes before PRINT_START in the job header. */
    const val HEADER_PADDING = 15

    /** Fixed feed in dots at the end of a continuous job. */
    const val CONTINUOUS_FEED_DOTS = 91

    /** ESC J n: print/feed n dot rows (0..255). */
    fun feedDots(n: Int): ByteArray {
        require(n in 0..255) { "Feed must be 0..255, was $n" }
        return byteArrayOf(0x1B, 0x4A, n.toByte())
    }

    // Status queries: write to PRINT_WRITE (FF02), response as notification on PRINT_NOTIFY (FF01).
    val QUERY_BATTERY = byteArrayOf(0x10, 0xFF.toByte(), 0x50, 0xF1.toByte())
    val QUERY_MODEL = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xF0.toByte())
    val QUERY_FIRMWARE = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xF1.toByte())
    val QUERY_SERIAL = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xF2.toByte())
    val QUERY_HARDWARE = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xEF.toByte())

    // Experimental command (not yet verified on the P15)
    val LEARN_GAP = byteArrayOf(0x10, 0xFF.toByte(), 0x03)

    // Transfer parameters
    const val CHUNK_SIZE = 96
    const val CHUNK_DELAY_MS = 30L
    const val FALLBACK_CHUNK_SIZE = 20
    const val COPY_DELAY_MS = 500L
    const val QUERY_GAP_MS = 20L
    const val MIN_MTU_FOR_FULL_CHUNKS = 99
    const val REQUESTED_MTU = 185
}
