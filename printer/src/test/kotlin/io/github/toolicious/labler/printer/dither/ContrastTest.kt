package io.github.toolicious.labler.printer.dither

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ContrastTest {

    @Test
    fun `amount 0 is the identity`() {
        val gray = floatArrayOf(0f, 64f, 128f, 200f, 255f)
        assertContentEquals(gray, Contrast.adjust(gray, 0))
    }

    @Test
    fun `positive value darkens the midtones`() {
        val out = Contrast.adjust(floatArrayOf(128f), 50)[0]
        assertTrue(out < 128f, "mid-gray should become darker, was $out")
    }

    @Test
    fun `light tone becomes black at a high value, but white at 0`() {
        // A light tone (200) must be able to be pushed up so that light graphics become visible.
        assertTrue(Contrast.adjust(floatArrayOf(200f), 0)[0] > 128f, "at 0 light = white side")
        assertTrue(Contrast.adjust(floatArrayOf(200f), 100)[0] < 128f, "at +100 the light tone becomes black")
    }

    @Test
    fun `very light tone already fills in the upper half, not only at the end`() {
        // An almost white tone (230, like light emoji areas) must tip over well before +100.
        assertTrue(Contrast.adjust(floatArrayOf(230f), 60)[0] < 128f, "at +60, 230 should already be black")
        assertTrue(Contrast.adjust(floatArrayOf(248f), 100)[0] < 128f, "at +100 even 248 becomes black (fully filled in)")
    }

    @Test
    fun `order is preserved (monotonic)`() {
        val out = Contrast.adjust(floatArrayOf(0f, 60f, 120f, 180f, 240f), 80)
        for (i in 1 until out.size) {
            assertTrue(out[i] >= out[i - 1], "must be monotonically increasing: ${out.toList()}")
        }
    }

    @Test
    fun `negative value brightens`() {
        val out = Contrast.adjust(floatArrayOf(64f), -60)[0]
        assertTrue(out > 64f, "dark tone should become lighter, was $out")
    }

    @Test
    fun `stays within the valid range`() {
        val gray = FloatArray(256) { it.toFloat() }
        assertTrue(Contrast.adjust(gray, 100).all { it in 0f..255f })
        assertTrue(Contrast.adjust(gray, -100).all { it in 0f..255f })
    }
}
