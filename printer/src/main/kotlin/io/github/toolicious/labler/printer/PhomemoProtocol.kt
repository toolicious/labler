package io.github.toolicious.labler.printer

import java.io.ByteArrayOutputStream

/**
 * Byte protocol of the P15/P12/L13 label printer family ("0x10" protocol with
 * embedded ESC/POS raster command GS v 0). Clean-room implementation based on
 * documented protocol facts; verified on the device.
 */
object PhomemoProtocol : PrinterProtocol {

    override val family = PrinterFamily.PHOMEMO

    // Print head geometry
    const val HEAD_DOTS = 96

    /**
     * How far the tape really advances, which is not the 203 dpi the print head is sold with.
     * Three measurements on two printers, a P15 and a P12, came out at 199.2, 199.6 and 200.1
     * dpi, close enough together that a single unit's tolerance cannot explain it: the feed is
     * geared to a round 200 while the head prints at 203. Assuming 203 makes anything printed
     * to scale 1.6 % long, which is 5 mm over a 300 mm ruler. A device that still misses can be
     * corrected in the settings.
     */
    const val FEED_DPI = 200

    override val geometry = HeadGeometry(
        headDots = HEAD_DOTS,
        dotsPerMm = FEED_DPI / HeadGeometry.MM_PER_INCH,
        bytesPerColumn = HEAD_DOTS / 8,
        minLengthMm = 10,
        maxLengthMm = 500,
        minTapeMm = 10,
        maxTapeMm = 15,
        tapeWidthsMm = listOf(12, 14, 15),
        // Commercially available die-cut labels (tape width x length in mm).
        diecutPresets = listOf(
            12 to 40,
            14 to 30, 14 to 40,
            15 to 30, 15 to 40,
        ),
    )

    override val ble = BleProfile(
        serviceUuid = "0000ff00-0000-1000-8000-00805f9b34fb",
        writeCharUuid = "0000ff02-0000-1000-8000-00805f9b34fb",
        // Status responses (battery/model/...) arrive as notifications on FF01 of the same service.
        notifyCharUuid = "0000ff01-0000-1000-8000-00805f9b34fb",
        // Name pattern "P15_xxxx_BLE" etc.
        namePrefixes = listOf("P15", "P12", "L13"),
        nameSuffix = "_BLE",
    )

    override val transport = TransportProfile(
        requestedMtu = 185,
        chunkSize = 96,
        fallbackChunkSize = 20,
        minMtuForFullChunks = 99,
        chunkDelayMs = 30L,
        copyDelayMs = 500L,
        queryGapMs = 20L,
    )

    override val supportedMedia = setOf(MediaType.DIE_CUT, MediaType.CONTINUOUS)

    /**
     * [FEED_DPI] is the average of what two printers measured, so an individual one can still
     * be half a percent off, and on a printed scale that shows. Everything else about this
     * family has been verified on the device and is not up for guessing.
     */
    override val tunables = setOf(Tunable.DOTS_PER_MM)

    override fun tunableValue(tunable: Tunable): String? =
        if (tunable == Tunable.DOTS_PER_MM) geometry.dotsPerMm.toString() else null

    override fun withTuning(tuning: ProtocolTuning): PrinterProtocol =
        tuning.float(Tunable.DOTS_PER_MM)
            ?.takeIf { it > 0f }
            ?.let { RegaugedFeed(this, geometry.copy(dotsPerMm = it)) }
            ?: this

    // Status queries: written to the write characteristic, answered as a notification on FF01.
    override val statusQueries = StatusQueries(
        battery = byteArrayOf(0x10, 0xFF.toByte(), 0x50, 0xF1.toByte()),
        model = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xF0.toByte()),
        firmware = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xF1.toByte()),
        serial = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xF2.toByte()),
        hardware = byteArrayOf(0x10, 0xFF.toByte(), 0x20, 0xEF.toByte()),
    )

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

    /**
     * Experimental darkness command from the newer "0x1F" command family: 1F 70 01 n (n = 1..15).
     * Not verified on the P15. Only emitted when the user enables it; besides setting the darkness
     * it also probes whether the device reacts to a 0x1F command at all.
     */
    fun density(level: Int): ByteArray {
        require(level in 1..15) { "Density must be 1..15, was $level" }
        return byteArrayOf(0x1F, 0x70, 0x01, level.toByte())
    }

    override fun packColumns(image: MonoImage): ByteArray = ColumnPacker.packColumns(image)

    /**
     * @param density experimental 0x1F darkness level (1..15) or null for the default protocol.
     *   When null the output is byte-identical to a job without density (default print path).
     */
    override fun buildJob(image: MonoImage, media: MediaType, density: Int?): ByteArray {
        require(media in supportedMedia) { "$media is not supported by $family" }
        require(image.height == HEAD_DOTS) {
            "Image must be $HEAD_DOTS dots high, was ${image.height}"
        }
        val payload = packColumns(image)
        val out = ByteArrayOutputStream(payload.size + 52)

        out.write(INIT)
        // Experimental darkness, right after init (init may reset printer state) and before the
        // raster header. Left out entirely for the default path so the golden byte stream is unchanged.
        if (density != null) out.write(PhomemoProtocol.density(density))
        repeat(HEADER_PADDING) { out.write(0) }
        out.write(PRINT_START)
        out.write(RASTER_GS_V0)
        out.write(image.bytesPerColumn)               // xL: 12 bytes per print line
        out.write(0)                                  // xH
        out.write(image.width and 0xFF)               // yL: line count = label length
        out.write((image.width shr 8) and 0xFF)       // yH (little-endian)
        out.write(payload)

        when (media) {
            MediaType.DIE_CUT -> {
                out.write(FORM_FEED)                  // advance to the die-cut gap
                out.write(PRINT_END)
                out.write(INIT)
                out.write(INIT)
            }
            MediaType.CONTINUOUS -> {
                out.write(feedDots(CONTINUOUS_FEED_DOTS))
                out.write(PRINT_END)
            }
        }
        return out.toByteArray()
    }
}
