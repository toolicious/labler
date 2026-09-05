package io.github.toolicious.labler.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading of the bitmap font format, against a real face rather than a made-up one.
 *
 * `5x7.pxf` in the test resources is the same file the app ships, so this covers what is easy to
 * get subtly wrong and impossible to see afterwards: the byte order of the header, the row stride,
 * and which end of a byte the leftmost dot sits at. A glyph that comes out mirrored or shifted
 * would still look like a glyph on the label, just the wrong one.
 */
class PixelFontTest {

    private fun face(): PixelFont {
        val stream = checkNotNull(javaClass.getResourceAsStream("/pixelfonts/5x7.pxf")) {
            "5x7.pxf is missing from the test resources"
        }
        return checkNotNull(PixelFont.read(stream.readBytes())) { "5x7.pxf did not parse" }
    }

    private fun PixelFont.render(character: Char): List<String> =
        (0 until cellHeight).map { y ->
            (0 until cellWidth).joinToString("") { x -> if (isDark(character.code, x, y)) "#" else "." }
        }

    @Test
    fun `the header describes the cell`() {
        val font = face()
        assertEquals(5, font.cellWidth)
        assertEquals(7, font.cellHeight)
        assertEquals(6, font.ascent)
    }

    @Test
    fun `a letter comes out the right way round`() {
        assertEquals(
            listOf(
                ".##..",
                "#..#.",
                "#..#.",
                "####.",
                "#..#.",
                "#..#.",
                ".....",
            ),
            face().render('A'),
        )
    }

    @Test
    fun `an umlaut keeps its dots above the letter`() {
        assertEquals(
            listOf(
                ".#.#.",
                ".....",
                ".###.",
                "#..#.",
                "#.##.",
                ".#.#.",
                ".....",
            ),
            face().render('ä'),
        )
    }

    @Test
    fun `the face carries what a label needs and says so`() {
        val font = face()
        // Latin-1 and Latin Extended-A, which is what the converter was told to keep.
        assertTrue(font.covers('A'.code))
        assertTrue(font.covers('ß'.code))
        assertTrue(font.covers('ł'.code))
        assertTrue(font.covers('€'.code))
        // Far outside the range; asking must not throw, it simply has nothing to draw.
        assertFalse(font.covers(0x4E2D))
        assertFalse(font.isDark(0x4E2D, 0, 0))
    }

    @Test
    fun `a file that is not one of ours is refused rather than misread`() {
        assertNull(PixelFont.read(ByteArray(0)))
        assertNull(PixelFont.read("not a font at all".toByteArray()))
        // Right magic, truncated body.
        assertNull(PixelFont.read("LPXF".toByteArray() + byteArrayOf(1, 5, 7, 6, 10, 0)))
    }

    @Test
    fun `the real file survives a round trip through the reader`() {
        assertNotNull(PixelFont.read(javaClass.getResourceAsStream("/pixelfonts/5x7.pxf")!!.readBytes()))
    }
}
