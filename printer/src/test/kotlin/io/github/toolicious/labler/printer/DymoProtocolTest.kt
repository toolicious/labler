package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DymoProtocolTest {

    private fun hex(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun blank(width: Int) = MonoImage.blank(width, DymoProtocol.HEAD_DOTS)

    private fun column(image: MonoImage, x: Int): ByteArray =
        DymoProtocol.packColumns(image).copyOfRange(x * 4, x * 4 + 4)

    // ----- Raster -----

    @Test
    fun `a white label packs to four zero bytes per column`() {
        val packed = DymoProtocol.packColumns(blank(3))
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
        val job = DymoProtocol.buildJob(blank(8), MediaType.CONTINUOUS)

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
        val job = DymoProtocol.buildJob(blank(320), MediaType.CONTINUOUS)
        assertContentEquals(hex(0x40, 0x01, 0x00, 0x00), job.copyOfRange(19, 23))
    }

    @Test
    fun `die-cut is refused, the tape is cut by hand`() {
        assertFailsWith<IllegalArgumentException> {
            DymoProtocol.buildJob(blank(8), MediaType.DIE_CUT)
        }
    }

    @Test
    fun `an image of the wrong head height is refused`() {
        assertFailsWith<IllegalArgumentException> {
            DymoProtocol.buildJob(MonoImage.blank(8, 96), MediaType.CONTINUOUS)
        }
    }

    // ----- Framing -----

    @Test
    fun `the header goes out on its own and the rest carries chunk indices`() {
        val job = DymoProtocol.buildJob(blank(8), MediaType.CONTINUOUS)
        val chunks = DymoProtocol.framePayload(job, DymoProtocol.transport.chunkSize)

        assertEquals(2, chunks.size)
        assertContentEquals(job.copyOfRange(0, 9), chunks[0])
        assertEquals(0x00.toByte(), chunks[1][0])
        assertContentEquals(job.copyOfRange(9, job.size), chunks[1].copyOfRange(1, chunks[1].size - 2))
        assertContentEquals(hex(0x12, 0x34), chunks[1].copyOfRange(chunks[1].size - 2, chunks[1].size))
    }

    @Test
    fun `a payload of exactly 500 bytes still fits one chunk`() {
        // 24 bytes of directives around the raster, so 119 columns make 476 + 24 = 500.
        val job = DymoProtocol.buildJob(blank(119), MediaType.CONTINUOUS)
        assertEquals(9 + 500, job.size)
        val chunks = DymoProtocol.framePayload(job, DymoProtocol.transport.chunkSize)
        assertEquals(listOf(9, 503), chunks.map { it.size })
    }

    @Test
    fun `one byte more starts a second chunk with the next index`() {
        // A column is four bytes, so the next size up from 500 is 504.
        val job = DymoProtocol.buildJob(blank(120), MediaType.CONTINUOUS)
        assertEquals(9 + 504, job.size)
        val chunks = DymoProtocol.framePayload(job, DymoProtocol.transport.chunkSize)
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
        val job = DymoProtocol.buildJob(blank(8), MediaType.CONTINUOUS)
        assertFailsWith<IllegalArgumentException> { DymoProtocol.framePayload(job, 20) }
    }

    // ----- Result -----

    @Test
    fun `the printer's own verdict is read off the notify channel`() {
        assertEquals(PrintResult.OK, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x01)))
        assertEquals(PrintResult.OK_LOW_BATTERY, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x03)))
        assertEquals(PrintResult.CANCELLED, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x04)))
        assertEquals(PrintResult.LOW_BATTERY, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x06)))
        assertEquals(PrintResult.NO_CASSETTE, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x07)))
        assertEquals(PrintResult.FAILED, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x02)))
        assertEquals(PrintResult.FAILED, DymoProtocol.parsePrintResult(hex(0x1B, 0x52, 0x05)))
    }

    @Test
    fun `anything that is not a result is ignored rather than guessed at`() {
        assertNull(DymoProtocol.parsePrintResult(hex(0x1B, 0x52)))
        assertNull(DymoProtocol.parsePrintResult(hex(0x00, 0x52, 0x01)))
        assertNull(DymoProtocol.parsePrintResult(ByteArray(0)))
    }

    // ----- Discovery and geometry -----

    @Test
    fun `the advertised name finds the family whatever its capitals`() {
        assertEquals(DymoProtocol, PrinterProtocols.matchName("Letratag58CF79ABCDEF"))
        assertEquals(DymoProtocol, PrinterProtocols.matchName("LETRATAG58CF79ABCDEF"))
        // The other family keeps its exact-case match.
        assertEquals(PhomemoProtocol, PrinterProtocols.matchName("P15_1234_BLE"))
        assertNull(PrinterProtocols.matchName("p15_1234_BLE"))
    }

    @Test
    fun `geometry is 30 dots on 12 mm tape`() {
        val g = DymoProtocol.geometry
        assertEquals(30, g.headDots)
        assertEquals(4, g.bytesPerColumn)
        assertEquals(listOf(12), g.tapeWidthsMm)
        assertEquals(emptyList(), g.diecutPresets)
        // 160 dpi, so a 40 mm label is 252 dots long.
        assertEquals(252, g.mmToDots(40))
        assertEquals(40, g.dotsToMm(252))
    }
}
