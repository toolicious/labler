package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DymoProtocolTest {

    private fun hex(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun blank(width: Int) = MonoImage.blank(width, DymoProtocol.HEAD_DOTS)

    private fun column(image: MonoImage, x: Int): ByteArray =
        DymoProtocol.DEFAULT.packColumns(image).copyOfRange(x * 4, x * 4 + 4)

    // ----- Raster -----

    @Test
    fun `a white label packs to four zero bytes per column`() {
        val packed = DymoProtocol.DEFAULT.packColumns(blank(3))
        assertEquals(12, packed.size)
        assertContentEquals(ByteArray(12), packed)
    }

    @Test
    fun `the topmost row is the second-highest bit of the last byte`() {
        // The reference shifts every row by one, which is what leaves 30 printable rows in a
        // 32-bit word. See DymoProtocol.ROW_BIT_OFFSET.
        val img = blank(2)
        img.setBlack(0, 0)
        assertContentEquals(hex(0x00, 0x00, 0x00, 0x40), column(img, 0))
        assertContentEquals(hex(0x00, 0x00, 0x00, 0x00), column(img, 1))
    }

    @Test
    fun `the byte boundary sits between row 6 and row 7`() {
        val sixth = blank(1).also { it.setBlack(0, 6) }
        assertContentEquals(hex(0x00, 0x00, 0x00, 0x01), column(sixth, 0))
        val seventh = blank(1).also { it.setBlack(0, 7) }
        assertContentEquals(hex(0x00, 0x00, 0x80.toInt(), 0x00), column(seventh, 0))
    }

    @Test
    fun `the bottom row lands in the first byte and leaves the last bit unused`() {
        val img = blank(1)
        img.setBlack(0, DymoProtocol.HEAD_DOTS - 1)
        assertContentEquals(hex(0x02, 0x00, 0x00, 0x00), column(img, 0))
    }

    @Test
    fun `a fully black column sets every bit but the two the head does not print`() {
        val img = blank(1)
        for (y in 0 until DymoProtocol.HEAD_DOTS) img.setBlack(0, y)
        // 30 rows in a 32-bit column, so one bit at either end of the range stays clear:
        // the high bit of the last byte in front of row 0, the low bit of the first behind row 29.
        assertContentEquals(hex(0xFE, 0xFF, 0xFF, 0x7F), column(img, 0))
    }

    // ----- Job -----

    @Test
    fun `a white 8-dot label has exactly the golden bytes`() {
        val job = DymoProtocol.DEFAULT.buildJob(blank(8), MediaType.CONTINUOUS)

        val payload = hex(0x1B, 0x73, 0x9A, 0x02, 0x00, 0x00) +   // start
            hex(0x1B, 0x44, 0x01, 0x02) +                          // print data, 1 bpp, alignment 2
            hex(0x08, 0x00, 0x00, 0x00) +                          // width, little-endian
            hex(0x20, 0x00, 0x00, 0x00) +                          // always the full 32-bit column
            ByteArray(8 * 4) +                                     // 8 columns x 4 bytes
            hex(0x1B, 0x45) +                                      // form feed
            hex(0x1B, 0x41) +                                      // status
            hex(0x1B, 0x51)                                        // end
        assertEquals(56, payload.size)

        // FF F0, magic, payload length, then the low byte of the sum of those eight.
        val header = hex(0xFF, 0xF0, 0x12, 0x34, 0x38, 0x00, 0x00, 0x00, 0x6D)

        assertEquals(65, job.size)
        assertContentEquals(header + payload, job)
    }

    @Test
    fun `the label length rides in the print data directive, little-endian`() {
        val job = DymoProtocol.DEFAULT.buildJob(blank(320), MediaType.CONTINUOUS)
        assertContentEquals(hex(0x40, 0x01, 0x00, 0x00), job.copyOfRange(19, 23))
    }

    @Test
    fun `die-cut is refused, the tape is cut by hand`() {
        assertFailsWith<IllegalArgumentException> {
            DymoProtocol.DEFAULT.buildJob(blank(8), MediaType.DIE_CUT)
        }
    }

    @Test
    fun `an image of the wrong head height is refused`() {
        assertFailsWith<IllegalArgumentException> {
            DymoProtocol.DEFAULT.buildJob(MonoImage.blank(8, 96), MediaType.CONTINUOUS)
        }
    }

    // ----- Framing -----

    @Test
    fun `the header goes out on its own and the rest carries chunk indices`() {
        val job = DymoProtocol.DEFAULT.buildJob(blank(8), MediaType.CONTINUOUS)
        val chunks = DymoProtocol.DEFAULT.framePayload(job, DymoProtocol.DEFAULT.transport.chunkSize)

        assertEquals(2, chunks.size)
        assertContentEquals(job.copyOfRange(0, 9), chunks[0])
        assertEquals(0x00.toByte(), chunks[1][0])
        assertContentEquals(job.copyOfRange(9, job.size), chunks[1].copyOfRange(1, chunks[1].size - 2))
        assertContentEquals(hex(0x12, 0x34), chunks[1].copyOfRange(chunks[1].size - 2, chunks[1].size))
    }

    @Test
    fun `a payload of exactly 500 bytes still fits one chunk`() {
        // 24 bytes of directives around the raster, so 119 columns make 476 + 24 = 500.
        val job = DymoProtocol.DEFAULT.buildJob(blank(119), MediaType.CONTINUOUS)
        assertEquals(9 + 500, job.size)
        val chunks = DymoProtocol.DEFAULT.framePayload(job, DymoProtocol.DEFAULT.transport.chunkSize)
        assertEquals(listOf(9, 503), chunks.map { it.size })
    }

    @Test
    fun `one byte more starts a second chunk with the next index`() {
        // A column is four bytes, so the next size up from 500 is 504.
        val job = DymoProtocol.DEFAULT.buildJob(blank(120), MediaType.CONTINUOUS)
        assertEquals(9 + 504, job.size)
        val chunks = DymoProtocol.DEFAULT.framePayload(job, DymoProtocol.DEFAULT.transport.chunkSize)
        assertEquals(listOf(9, 501, 7), chunks.map { it.size })
        assertEquals(0x00.toByte(), chunks[1][0])
        assertEquals(0x01.toByte(), chunks[2][0])
        // Reassembling the chunks without index and magic gives the job back.
        val body = chunks.drop(1).mapIndexed { i, c ->
            val end = if (i == chunks.size - 2) c.size - 2 else c.size
            c.copyOfRange(1, end)
        }.reduce { a, b -> a + b }
        assertContentEquals(job.copyOfRange(9, job.size), body)
    }

    @Test
    fun `a chunk size the frame does not fit into is refused`() {
        val job = DymoProtocol.DEFAULT.buildJob(blank(8), MediaType.CONTINUOUS)
        assertFailsWith<IllegalArgumentException> { DymoProtocol.DEFAULT.framePayload(job, 20) }
    }

    @Test
    fun `a packet smaller than a full chunk only means more chunks`() {
        // What a Motorola Edge 2021 negotiated: 500 bytes of MTU, 497 of them usable. 500 is
        // the most a chunk may carry, not the least, so the job goes out in smaller pieces.
        val job = DymoProtocol.DEFAULT.buildJob(blank(300), MediaType.CONTINUOUS)
        assertEquals(9 + 1224, job.size)
        val chunks = DymoProtocol.DEFAULT.framePayload(job, 497)
        assertEquals(listOf(9, 495, 495, 239), chunks.map { it.size })
        assertTrue(chunks.all { it.size <= 497 })
        assertContentEquals(intArrayOf(0, 1, 2), chunks.drop(1).map { it[0].toInt() }.toIntArray())

        // Reassembling the chunks without index and magic gives the job back.
        val body = chunks.drop(1).mapIndexed { i, c ->
            val end = if (i == chunks.size - 2) c.size - 2 else c.size
            c.copyOfRange(1, end)
        }.reduce { a, b -> a + b }
        assertContentEquals(job.copyOfRange(9, job.size), body)
    }

    @Test
    fun `the chunk index skips 27, the way the vendor app does`() {
        // 100 bytes a chunk is the smallest this family accepts, and 29 of them get past the gap.
        val job = DymoProtocol.DEFAULT.buildJob(blank(719), MediaType.CONTINUOUS)
        assertEquals(9 + 2900, job.size)
        val indices = DymoProtocol.DEFAULT.framePayload(job, DymoProtocol.DEFAULT.transport.minChunkSize)
            .drop(1)
            .map { it[0].toInt() }
        assertEquals(29, indices.size)
        assertEquals(listOf(25, 26, 28, 29), indices.takeLast(4))
        assertEquals(emptyList(), indices.filter { it == 27 })
    }

    @Test
    fun `even the longest label keeps its indices inside one byte`() {
        val columns = DymoProtocol.DEFAULT.geometry.maxLengthDots
        val job = DymoProtocol.DEFAULT.buildJob(blank(columns), MediaType.CONTINUOUS)
        val indices = DymoProtocol.DEFAULT.framePayload(job, DymoProtocol.DEFAULT.transport.minChunkSize)
            .drop(1)
            .map { it[0].toInt() }
        assertTrue(indices.max() <= 0xFF, "highest index ${indices.max()} for $columns columns")
    }

    // ----- Result -----

    @Test
    fun `the printer's own verdict is read off the notify channel`() {
        assertEquals(PrintResult.OK, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x01)))
        assertEquals(PrintResult.OK_LOW_BATTERY, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x03)))
        assertEquals(PrintResult.CANCELLED, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x04)))
        assertEquals(PrintResult.LOW_BATTERY, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x06)))
        assertEquals(PrintResult.NO_CASSETTE, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x07)))
        assertEquals(PrintResult.FAILED, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x02)))
        assertEquals(PrintResult.FAILED, DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52, 0x05)))
    }

    @Test
    fun `anything that is not a result is ignored rather than guessed at`() {
        assertNull(DymoProtocol.DEFAULT.parsePrintResult(hex(0x1B, 0x52)))
        assertNull(DymoProtocol.DEFAULT.parsePrintResult(hex(0x00, 0x52, 0x01)))
        assertNull(DymoProtocol.DEFAULT.parsePrintResult(ByteArray(0)))
    }

    // ----- Discovery and geometry -----

    @Test
    fun `the advertised name finds the family whatever its capitals`() {
        assertEquals(DymoProtocol.DEFAULT, PrinterProtocols.matchName("Letratag58CF79ABCDEF"))
        assertEquals(DymoProtocol.DEFAULT, PrinterProtocols.matchName("LETRATAG58CF79ABCDEF"))
        // The other family keeps its exact-case match.
        assertEquals(PhomemoProtocol, PrinterProtocols.matchName("P15_1234_BLE"))
        assertNull(PrinterProtocols.matchName("p15_1234_BLE"))
    }

    @Test
    fun `geometry is 30 dots on 12 mm tape`() {
        val g = DymoProtocol.DEFAULT.geometry
        assertEquals(30, g.headDots)
        assertEquals(4, g.bytesPerColumn)
        assertEquals(listOf(12), g.tapeWidthsMm)
        assertEquals(emptyList(), g.diecutPresets)
        // 160 dpi, so a 40 mm label is 252 dots long.
        assertEquals(252, g.mmToDots(40))
        assertEquals(40, g.dotsToMm(252))
    }

    // ----- Calibration -----

    private fun tuned(vararg pairs: Pair<Tunable, String>) =
        DymoProtocol.DEFAULT.withTuning(ProtocolTuning(mapOf(*pairs)))

    @Test
    fun `no overrides hands back the documented protocol itself`() {
        assertSame(DymoProtocol.DEFAULT, DymoProtocol.DEFAULT.withTuning(ProtocolTuning.NONE))
    }

    @Test
    fun `dropping the row offset moves the top row to the high bit`() {
        val img = blank(1).also { it.setBlack(0, 0) }
        val packed = tuned(Tunable.ROW_BIT_OFFSET to "0").packColumns(img)
        assertContentEquals(hex(0x00, 0x00, 0x00, 0x80), packed)
    }

    @Test
    fun `un-reversing the byte order puts the top row in the first byte`() {
        val img = blank(1).also { it.setBlack(0, 0) }
        val packed = tuned(Tunable.REVERSE_COLUMN_BYTES to "false").packColumns(img)
        assertContentEquals(hex(0x40, 0x00, 0x00, 0x00), packed)
    }

    @Test
    fun `a measured dot pitch replaces the advertised one`() {
        val g = tuned(Tunable.DOTS_PER_MM to "5.0").geometry
        assertEquals(200, g.mmToDots(40))
        // Everything else about the head is untouched.
        assertEquals(30, g.headDots)
    }

    @Test
    fun `a taller head is accepted and raises the required image height`() {
        val protocol = tuned(Tunable.HEAD_DOTS to "32")
        assertEquals(32, protocol.geometry.headDots)
        protocol.buildJob(MonoImage.blank(4, 32), MediaType.CONTINUOUS)
        assertFailsWith<IllegalArgumentException> {
            protocol.buildJob(blank(4), MediaType.CONTINUOUS)
        }
    }

    @Test
    fun `waiting for the printer's verdict can be switched off`() {
        assertFalse(tuned(Tunable.AWAIT_PRINT_RESULT to "false").awaitsPrintResult)
        assertTrue(DymoProtocol.DEFAULT.awaitsPrintResult)
    }

    @Test
    fun `an unreadable override is ignored rather than crashing the printer path`() {
        val protocol = tuned(Tunable.ROW_BIT_OFFSET to "nonsense", Tunable.DOTS_PER_MM to "")
        assertContentEquals(
            DymoProtocol.DEFAULT.packColumns(blank(1).also { it.setBlack(0, 0) }),
            protocol.packColumns(blank(1).also { it.setBlack(0, 0) }),
        )
        assertEquals(DymoProtocol.DEFAULT.geometry.dotsPerMm, protocol.geometry.dotsPerMm)
    }

    @Test
    fun `the registry hands out the tuned protocol and takes it back again`() {
        try {
            PrinterProtocols.applyTuning(
                mapOf(PrinterFamily.DYMO to ProtocolTuning(mapOf(Tunable.HEAD_DOTS to "32")))
            )
            assertEquals(32, PrinterProtocols.of(PrinterFamily.DYMO).geometry.headDots)
            // The other family has nothing to tune and is handed back untouched.
            assertSame(PhomemoProtocol, PrinterProtocols.of(PrinterFamily.PHOMEMO))
        } finally {
            PrinterProtocols.applyTuning(emptyMap())
        }
        assertEquals(30, PrinterProtocols.of(PrinterFamily.DYMO).geometry.headDots)
    }
}
