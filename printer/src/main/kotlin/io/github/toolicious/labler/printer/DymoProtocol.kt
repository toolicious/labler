package io.github.toolicious.labler.printer

import java.io.ByteArrayOutputStream

/**
 * Byte protocol of the Dymo LetraTag 200B: escape directives wrapped in a checksummed frame,
 * written in indexed chunks. Clean-room implementation from the protocol description published
 * by the dymo-bluetooth project; not yet verified on a device.
 *
 * The print head is 30 dots across and the label runs lengthwise under it, the same shape the
 * Phomemo family has, only a third of the height and on a coarser dot grid.
 */
object DymoProtocol : PrinterProtocol {

    override val family = PrinterFamily.DYMO

    /** Rows the head actually prints. The wire format pads them out to a whole 32-bit column. */
    const val HEAD_DOTS = 30

    /** Bits one column occupies on the wire, whatever the head prints of them. */
    const val COLUMN_BITS = 32

    /** Dymo's figure for the LetraTag 200B. Only the mm readout depends on it, never the raster. */
    const val DPI = 160

    override val geometry = HeadGeometry(
        headDots = HEAD_DOTS,
        dotsPerMm = DPI / 25.4f,
        bytesPerColumn = COLUMN_BITS / 8,
        minLengthMm = 10,
        maxLengthMm = 500,
        // One cartridge width exists, so both bounds and the list say the same thing.
        minTapeMm = 12,
        maxTapeMm = 12,
        tapeWidthsMm = listOf(12),
        // Continuous tape only; the user cuts it at the device.
        diecutPresets = emptyList(),
    )

    override val ble = BleProfile(
        serviceUuid = "be3dd650-2b3d-42f1-99c1-f0f749dd0678",
        writeCharUuid = "be3dd651-2b3d-42f1-99c1-f0f749dd0678",
        notifyCharUuid = "be3dd652-2b3d-42f1-99c1-f0f749dd0678",
        // Advertised as "Letratag" followed by the MAC address without its colons. Which
        // letters are capitals is not worth betting a failed scan on.
        namePrefixes = listOf("Letratag"),
        ignoreNameCase = true,
    )

    override val transport = TransportProfile(
        requestedMtu = 517,
        // Index byte + payload + the trailing magic of the final chunk.
        chunkSize = 1 + PAYLOAD_CHUNK + MAGIC_SIZE,
        fallbackChunkSize = 1 + PAYLOAD_CHUNK + MAGIC_SIZE,
        // ATT keeps three bytes of every packet for itself.
        minMtuForFullChunks = 1 + PAYLOAD_CHUNK + MAGIC_SIZE + 3,
        chunkDelayMs = 20L,
        copyDelayMs = 1_500L,
        queryGapMs = 20L,
        // The chunk index is part of the format, so a short write is a broken job, not a slow one.
        requiresFullChunks = true,
    )

    override val supportedMedia = setOf(MediaType.CONTINUOUS)

    /** The printer answers by itself when a job is through; there is nothing to ask it. */
    override val statusQueries: StatusQueries? = null

    override val awaitsPrintResult = true

    // Frame
    private const val HEADER_SIZE = 9
    private const val MAGIC_SIZE = 2
    private val MAGIC = byteArrayOf(0x12, 0x34)

    /** Payload bytes per chunk, on top of the index byte in front of them. */
    private const val PAYLOAD_CHUNK = 500

    // Directives: escape plus one letter.
    private val START = byteArrayOf(0x1B, 0x73, 0x9A.toByte(), 0x02, 0x00, 0x00)
    private val PRINT_DATA = byteArrayOf(0x1B, 0x44, 0x01, 0x02)
    private val FORM_FEED = byteArrayOf(0x1B, 0x45)
    private val STATUS = byteArrayOf(0x1B, 0x41)
    private val END = byteArrayOf(0x1B, 0x51)

    /** First two bytes of a result the printer pushes: ESC R. */
    private const val RESULT_ESC = 0x1B
    private const val RESULT_R = 0x52

    /**
     * Where the topmost printable row sits inside the 32-bit column.
     *
     * The reference implementation shifts every row by one, which leaves the first and last bit
     * of the word unused and accounts for the 30 printable rows out of 32. Its own README instead
     * shows a black top-left pixel as 00 00 00 80, which is this offset at zero. The code is the
     * half that has demonstrably printed, so it wins; if the beta comes back shifted by one row,
     * this constant is the whole correction.
     */
    const val ROW_BIT_OFFSET = 1

    /**
     * One column of 4 bytes per label column, most significant byte last: row 0 lands in the last
     * byte, the bottom of the head in the first, and within a byte the topmost row is the high bit.
     */
    override fun packColumns(image: MonoImage): ByteArray {
        val bytesPerColumn = geometry.bytesPerColumn
        val out = ByteArray(image.width * bytesPerColumn)
        for (x in 0 until image.width) {
            val base = x * bytesPerColumn
            for (y in 0 until image.height) {
                if (!image.isBlack(x, y)) continue
                val bit = y + ROW_BIT_OFFSET
                val index = base + bytesPerColumn - 1 - bit / 8
                out[index] = (out[index].toInt() or (0x80 ushr (bit % 8))).toByte()
            }
        }
        return out
    }

    override fun buildJob(image: MonoImage, media: MediaType, density: Int?): ByteArray {
        require(media in supportedMedia) { "$media is not supported by $family" }
        require(image.height == HEAD_DOTS) {
            "Image must be $HEAD_DOTS dots high, was ${image.height}"
        }
        val body = ByteArrayOutputStream(image.width * geometry.bytesPerColumn + 32)
        body.write(START)
        body.write(PRINT_DATA)
        body.write(littleEndian32(image.width))
        // Always the full column, whatever part of it the head prints.
        body.write(littleEndian32(COLUMN_BITS))
        body.write(packColumns(image))
        body.write(FORM_FEED)
        body.write(STATUS)
        body.write(END)

        val payload = body.toByteArray()
        return frameHeader(payload.size) + payload
    }

    /**
     * The header goes out on its own, then the payload in chunks that each carry their index.
     * The final chunk ends on the same magic the header opens with.
     */
    override fun framePayload(job: ByteArray, chunkSize: Int): List<ByteArray> {
        require(job.size > HEADER_SIZE) { "Job is shorter than its own header" }
        require(chunkSize >= transport.chunkSize) {
            "A chunk of $chunkSize bytes cannot hold a framed chunk of ${transport.chunkSize}"
        }
        val out = ArrayList<ByteArray>(2 + (job.size - HEADER_SIZE) / PAYLOAD_CHUNK)
        out.add(job.copyOfRange(0, HEADER_SIZE))

        var pos = HEADER_SIZE
        var index = 0
        while (pos < job.size) {
            val end = minOf(pos + PAYLOAD_CHUNK, job.size)
            val last = end == job.size
            val chunk = ByteArray(1 + (end - pos) + if (last) MAGIC_SIZE else 0)
            chunk[0] = index.toByte()
            job.copyInto(chunk, 1, pos, end)
            if (last) MAGIC.copyInto(chunk, chunk.size - MAGIC_SIZE)
            out.add(chunk)
            pos = end
            index++
        }
        return out
    }

    override fun parsePrintResult(bytes: ByteArray): PrintResult? {
        if (bytes.size < 3) return null
        if (bytes[0].toInt() != RESULT_ESC || bytes[1].toInt() != RESULT_R) return null
        return when (bytes[2].toInt() and 0xFF) {
            0, 1 -> PrintResult.OK
            3 -> PrintResult.OK_LOW_BATTERY
            4 -> PrintResult.CANCELLED
            6 -> PrintResult.LOW_BATTERY
            7 -> PrintResult.NO_CASSETTE
            else -> PrintResult.FAILED
        }
    }

    /** FF F0, the magic, the payload length and a checksum over the eight bytes in front of it. */
    private fun frameHeader(payloadLength: Int): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        header[0] = 0xFF.toByte()
        header[1] = 0xF0.toByte()
        MAGIC.copyInto(header, 2)
        littleEndian32(payloadLength).copyInto(header, 4)
        var sum = 0
        for (i in 0 until HEADER_SIZE - 1) sum += header[i].toInt() and 0xFF
        header[HEADER_SIZE - 1] = (sum and 0xFF).toByte()
        return header
    }

    private fun littleEndian32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}
