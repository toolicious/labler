package io.github.toolicious.labler.printer.dither

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutlineTest {

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `uniform area is outlined only at the silhouette`() {
        val w = 5
        val h = 5
        val argb = IntArray(w * h) { rgb(128, 128, 128) }
        val glyph = BooleanArray(w * h) { true }
        val out = Outline.trace(argb, glyph, w, h, 100, 1)
        assertTrue(out[0], "corner lies on the silhouette")
        assertFalse(out[2 * w + 2], "center without a color jump carries no line")
    }

    @Test
    fun `color edge is detected despite equal brightness`() {
        val w = 5
        val h = 5
        val argb = IntArray(w * h) { i -> if ((i % w) < 3) rgb(255, 0, 0) else rgb(0, 0, 255) }
        val glyph = BooleanArray(w * h) { true }
        val out = Outline.trace(argb, glyph, w, h, 100, 1)
        assertTrue(out[2 * w + 2], "color edge at column 2 (inkier side) is detected")
        assertFalse(out[2 * w + 3], "the other side stays clear, no double line")
    }

    @Test
    fun `thin line is adopted instead of outlined on both sides`() {
        val w = 5
        val h = 5
        val argb = IntArray(w * h) { i -> if (i / w == 2) rgb(40, 40, 40) else rgb(255, 255, 255) }
        val glyph = BooleanArray(w * h) { true }
        val out = Outline.trace(argb, glyph, w, h, 100, 1)
        assertTrue(out[2 * w + 2], "the dark row itself is drawn")
        assertFalse(out[1 * w + 2], "white above the line stays clear (no doubling)")
        assertFalse(out[3 * w + 2], "white below the line stays clear (no doubling)")
    }

    @Test
    fun `higher sensitivity only adds weaker edges (monotonic)`() {
        val w = 6
        val h = 5
        val argb = IntArray(w * h) { i ->
            when (i % w) {
                0, 1 -> rgb(0, 0, 0)
                2, 3 -> rgb(60, 60, 60)
                else -> rgb(255, 255, 255)
            }
        }
        val glyph = BooleanArray(w * h) { true }
        // Default (lines / threshold): the stronger edge (mag 195) crosses the threshold before the
        // weaker one (mag 60). At ~mid slider the strong edge is fully shown, the weak one not yet.
        val mid = Outline.trace(argb, glyph, w, h, 55, 1)
        val high = Outline.trace(argb, glyph, w, h, 100, 1)
        val weak = 2 * w + 1    // black (inky) side of the black|gray edge
        val strong = 2 * w + 3  // gray (inky) side of the gray|white edge
        assertTrue(high[weak] && high[strong], "at max sensitivity both edges are drawn")
        assertTrue(mid[strong], "the stronger edge is revealed before the weaker one")
        assertFalse(mid[weak], "the weaker edge is not yet shown at mid sensitivity")
        for (i in argb.indices) if (mid[i]) assertTrue(high[i], "a line must not disappear as sensitivity rises")
    }

    @Test
    fun `below the threshold only silhouette, inner edges off`() {
        val w = 5
        val h = 5
        // Left black, right white: strong inner edge, but sensitivity < 10.
        val argb = IntArray(w * h) { i -> if ((i % w) < 3) rgb(0, 0, 0) else rgb(255, 255, 255) }
        val glyph = BooleanArray(w * h) { true }
        val out = Outline.trace(argb, glyph, w, h, 5, 1)
        assertTrue(out[0], "the silhouette remains")
        assertFalse(out[2 * w + 2], "strong inner edge is ignored below 10")
    }

    @Test
    fun `greater thickness widens the line`() {
        val w = 7
        val h = 7
        val argb = IntArray(w * h) { i -> if (i / w == 3) rgb(40, 40, 40) else rgb(255, 255, 255) }
        val glyph = BooleanArray(w * h) { true }
        val thin = Outline.trace(argb, glyph, w, h, 100, 1)
        val thick = Outline.trace(argb, glyph, w, h, 100, 3)
        assertTrue(thick.count { it } > thin.count { it }, "thicker = more set pixels")
        assertFalse(thin[2 * w + 3], "thin: row above the line stays clear")
        assertTrue(thick[2 * w + 3], "thick: row above the line is drawn along")
    }

    @Test
    fun `background pixels never carry a line`() {
        val w = 4
        val h = 4
        val argb = IntArray(w * h) { rgb(0, 0, 0) }
        val glyph = BooleanArray(w * h) { false }
        val out = Outline.trace(argb, glyph, w, h, 100, 1)
        assertFalse(out.any { it }, "no line without glyph pixels")
    }
}
