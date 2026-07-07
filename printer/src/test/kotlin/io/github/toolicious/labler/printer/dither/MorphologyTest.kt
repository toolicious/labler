package io.github.toolicious.labler.printer.dither

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorphologyTest {

    @Test
    fun `drops small components, keeps connected lines`() {
        val w = 10
        val h = 10
        val mask = BooleanArray(w * h)
        mask[2 * w + 2] = true // a single isolated speck
        for (x in 1..8) mask[5 * w + x] = true // an 8 px connected line

        val out = Morphology.despeckle(mask, w, h, 5)
        assertFalse(out[2 * w + 2], "the isolated speck is removed")
        assertTrue(out[5 * w + 3], "the connected line is kept")
        assertTrue(out[5 * w + 7], "the whole line is kept")
    }

    @Test
    fun `smooth shortens fringes and keeps the main line`() {
        val w = 14
        val h = 8
        val mask = BooleanArray(w * h)
        for (x in 1..12) mask[4 * w + x] = true // main line
        mask[3 * w + 6] = true // a 3 px fringe up from the line
        mask[2 * w + 6] = true
        mask[1 * w + 6] = true

        val out = Morphology.smooth(mask, w, h)
        assertFalse(out[1 * w + 6], "fringe tip removed")
        assertFalse(out[2 * w + 6], "fringe shortened")
        assertTrue(out[4 * w + 6], "the main line survives")
        assertTrue(out[4 * w + 3], "the line body survives")
    }
}
