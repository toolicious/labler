package io.github.toolicious.labler.printer.dither

import kotlin.test.Test
import kotlin.test.assertTrue

class CannyTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `blank image has no edges`() {
        val w = 10
        val h = 6
        val white = IntArray(w * h) { rgb(255, 255, 255) }
        val glyph = BooleanArray(w * h) { true }
        assertTrue(Canny.detect(white, glyph, w, h, 70, 1).none { it }, "a blank image has no edges")
    }

    @Test
    fun `strong edge becomes a thin line near the boundary`() {
        val w = 10
        val h = 6
        val argb = IntArray(w * h) { i -> if ((i % w) < 5) rgb(0, 0, 0) else rgb(255, 255, 255) }
        val glyph = BooleanArray(w * h) { true }
        val out = Canny.detect(argb, glyph, w, h, 70, 1)
        val count = out.count { it }
        assertTrue(count in 1..(3 * h), "edge is a thin line, not a blob (count=$count)")
        var nearBoundary = 0
        for (y in 0 until h) for (x in 3..6) if (out[y * w + x]) nearBoundary++
        assertTrue(nearBoundary > 0, "the edge sits near the black/white boundary")
    }

    @Test
    fun `isolated weak speck is dropped, strong edge kept`() {
        val w = 12
        val h = 8
        // Strong black|white edge on the left, plus a single faint gray speck far in the white area.
        val argb = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x < 4 -> rgb(0, 0, 0)
                x == 9 && y == 4 -> rgb(210, 210, 210) // faint isolated speck
                else -> rgb(255, 255, 255)
            }
        }
        val glyph = BooleanArray(w * h) { true }
        val out = Canny.detect(argb, glyph, w, h, 60, 1)
        // Strong edge is present near the x=4 boundary.
        var boundary = 0
        for (y in 0 until h) for (x in 3..5) if (out[y * w + x]) boundary++
        assertTrue(boundary > 0, "the strong edge is kept")
        // The faint isolated speck and its immediate surroundings are dropped by hysteresis.
        var speck = 0
        for (yy in 3..5) for (xx in 8..10) if (out[yy * w + xx]) speck++
        assertTrue(speck == 0, "an isolated faint speck is suppressed (found $speck)")
    }
}
