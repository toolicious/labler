package io.github.toolicious.labler.printer

import java.io.ByteArrayOutputStream

/**
 * Byte protocol of the Dymo LetraTag 200B: escape directives wrapped in a checksummed frame,
 * written in indexed chunks. Clean-room implementation from the protocol description published
 * by the dymo-bluetooth project; not yet verified on a device.
 *
 * The print head is 30 dots across and the label runs lengthwise under it, the same shape the
 * Phomemo family has, only a third of the height and on a coarser dot grid.
 *
 * A class rather than an object because nobody here owns the printer: the four values that could
 * not be settled from the description are [Tunable]s, and a development build hands a tester an
 * instance with their guesses in it. [DEFAULT] is what everything else uses.
 */
class DymoProtocol private constructor(tuning: ProtocolTuning) : PrinterProtocol {

    override val family = PrinterFamily.DYMO

    /** Rows the head prints, of the 32 bits a column occupies on the wire. */
    val headDots: Int = tuning.int(Tunable.HEAD_DOTS) ?: HEAD_DOTS

    /**
     * Where the topmost printable row sits inside the 32-bit column.
     *
     * The reference implementation shifts every row by one, which leaves the first and last bit
     * of the word unused and accounts for the 30 printable rows out of 32. Its own README instead
     * shows a black top-left pixel as 00 00 00 80, which is this offset at zero. The code is the
     * half that has demonstrably printed, so it is the default.
     */
    val rowBitOffset: Int = tuning.int(Tunable.ROW_BIT_OFFSET) ?: ROW_BIT_OFFSET

    /** Whether row 0 belongs in the last byte of a column (the default) or the first. */
    val reverseColumnBytes: Boolean =
        tuning.bool(Tunable.REVERSE_COLUMN_BYTES) ?: REVERSE_COLUMN_BYTES

    override val geometry = HeadGeometry(
        headDots = headDots,
        dotsPerMm = tuning.float(Tunable.DOTS_PER_MM) ?: (DPI / 25.4f),
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
        // 500 is what a chunk may hold at most, not what it has to hold, so a smaller packet
        // only means more chunks. The floor is the index: it is one byte, and the longest
        // label this family prints has to stay inside it.
        minChunkSize = 1 + MIN_PAYLOAD_CHUNK + MAGIC_SIZE,
        chunkDelayMs = 20L,
        copyDelayMs = 1_500L,
        queryGapMs = 20L,
    )

    override val supportedMedia = setOf(MediaType.CONTINUOUS)

    /** The printer answers by itself when a job is through; there is nothing to ask it. */
    override val statusQueries: StatusQueries? = null

    override val awaitsPrintResult =
        tuning.bool(Tunable.AWAIT_PRINT_RESULT) ?: AWAIT_PRINT_RESULT

    override val tunables = setOf(
        Tunable.DOTS_PER_MM,
        Tunable.HEAD_DOTS,
        Tunable.ROW_BIT_OFFSET,
        Tunable.REVERSE_COLUMN_BYTES,
        Tunable.AWAIT_PRINT_RESULT,
    )

    override fun withTuning(tuning: ProtocolTuning): PrinterProtocol =
        if (tuning.isEmpty) DEFAULT else DymoProtocol(tuning)

    override fun tunableValue(tunable: Tunable): String? = when (tunable) {
        Tunable.DOTS_PER_MM -> geometry.dotsPerMm.toString()
        Tunable.HEAD_DOTS -> headDots.toString()
        Tunable.ROW_BIT_OFFSET -> rowBitOffset.toString()
        Tunable.REVERSE_COLUMN_BYTES -> reverseColumnBytes.toString()
        Tunable.AWAIT_PRINT_RESULT -> awaitsPrintResult.toString()
    }

    /**
     * Four bytes per label column. Row 0 lands in the last byte and the bottom of the head in the
     * first, and within a byte the topmost row is the high bit.
     */
    override fun packColumns(image: MonoImage): ByteArray {
        val bytesPerColumn = geometry.bytesPerColumn
        val out = ByteArray(image.width * bytesPerColumn)
        for (x in 0 until image.width) {
            val base = x * bytesPerColumn
            for (y in 0 until image.height) {
                if (!image.isBlack(x, y)) continue
                val bit = y + rowBitOffset
                val byte = bit / 8
                val index = base + if (reverseColumnBytes) bytesPerColumn - 1 - byte else byte
                out[index] = (out[index].toInt() or (0x80 ushr (bit % 8))).toByte()
            }
        }
        return out
    }

    override fun buildJob(image: MonoImage, media: MediaType, density: Int?): ByteArray {
        require(media in supportedMedia) { "$media is not supported by $family" }
        require(image.height == headDots) {
            "Image must be $headDots dots high, was ${image.height}"
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
        require(chunkSize >= transport.minChunkSize) {
            "A chunk of $chunkSize bytes is below the ${transport.minChunkSize} this family needs"
        }
        // Whatever the phone negotiated, minus the frame around the payload. The magic only
        // rides along on the last chunk, but leaving room for it everywhere keeps this to one
        // number and costs two bytes a chunk.
        val payloadChunk = minOf(PAYLOAD_CHUNK, chunkSize - 1 - MAGIC_SIZE)
        val out = ArrayList<ByteArray>(2 + (job.size - HEADER_SIZE) / payloadChunk)
        out.add(job.copyOfRange(0, HEADER_SIZE))

        var pos = HEADER_SIZE
        var sequence = 0
        while (pos < job.size) {
            val end = minOf(pos + payloadChunk, job.size)
            val last = end == job.size
            val chunk = ByteArray(1 + (end - pos) + if (last) MAGIC_SIZE else 0)
            chunk[0] = chunkIndex(sequence).toByte()
            job.copyInto(chunk, 1, pos, end)
            if (last) MAGIC.copyInto(chunk, chunk.size - MAGIC_SIZE)
            out.add(chunk)
            pos = end
            sequence++
        }
        return out
    }

    /**
     * The number a chunk carries in front of its data.
     *
     * The vendor app never sends [SKIPPED_INDEX]; why is not documented anywhere, and the
     * reference implementation copies the gap without knowing either. At the full chunk size no
     * label this family can print reaches that far, but a phone that grants a smaller packet
     * makes more chunks out of the same job and does.
     */
    private fun chunkIndex(sequence: Int): Int =
        if (sequence >= SKIPPED_INDEX) sequence + 1 else sequence

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

    companion object {
        /** The protocol as documented, which is what everything but a calibration run uses. */
        val DEFAULT = DymoProtocol(ProtocolTuning.NONE)

        /** Rows the head actually prints. The wire format pads them out to a whole 32-bit column. */
        const val HEAD_DOTS = 30

        /** Bits one column occupies on the wire, whatever the head prints of them. */
        const val COLUMN_BITS = 32

        /** Dymo's figure for the LetraTag 200B. Only the mm readout depends on it, never the raster. */
        const val DPI = 160

        const val ROW_BIT_OFFSET = 1
        const val REVERSE_COLUMN_BYTES = true
        const val AWAIT_PRINT_RESULT = true

        // Frame
        private const val HEADER_SIZE = 9
        private const val MAGIC_SIZE = 2
        private val MAGIC = byteArrayOf(0x12, 0x34)

        /** Payload bytes per chunk, on top of the index byte in front of them. */
        private const val PAYLOAD_CHUNK = 500

        /**
         * Smallest payload a chunk may carry. The index is one byte, so the longest job this
         * family builds, around 12.6 kB at 500 mm, has to fit in 255 chunks; 100 leaves room
         * to spare and asks no more of a phone than a 106 byte packet.
         */
        private const val MIN_PAYLOAD_CHUNK = 100

        /** The index the vendor app leaves out. */
        private const val SKIPPED_INDEX = 27

        // Directives: escape plus one letter.
        private val START = byteArrayOf(0x1B, 0x73, 0x9A.toByte(), 0x02, 0x00, 0x00)
        private val PRINT_DATA = byteArrayOf(0x1B, 0x44, 0x01, 0x02)
        private val FORM_FEED = byteArrayOf(0x1B, 0x45)
        private val STATUS = byteArrayOf(0x1B, 0x41)
        private val END = byteArrayOf(0x1B, 0x51)

        /** First two bytes of a result the printer pushes: ESC R. */
        private const val RESULT_ESC = 0x1B
        private const val RESULT_R = 0x52
    }
}
