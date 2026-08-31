package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the mm/dot conversions to what the app did with plain integer arithmetic before the
 * geometry became per-family and dotsPerMm a Float. A single mismatch here would move every
 * element of every existing label.
 */
class HeadGeometryTest {

    private val phomemo = PhomemoProtocol.geometry

    @Test
    fun `millimetres to dots stays exactly mm times 8 over the whole length range`() {
        for (mm in 0..PhomemoProtocol.geometry.maxLengthMm) {
            assertEquals(mm * 8, phomemo.mmToDots(mm), "at $mm mm")
        }
    }

    @Test
    fun `a leading edge pulled into the negative converts exactly too`() {
        for (mm in -500..0) {
            assertEquals(mm * 8, phomemo.mmToDots(mm), "at $mm mm")
        }
    }

    @Test
    fun `dots back to millimetres truncates like the former integer division`() {
        for (dots in 0..4000) {
            assertEquals(dots / 8, phomemo.dotsToMm(dots), "at $dots dots")
        }
    }

    @Test
    fun `derived bounds match the former constants`() {
        assertEquals(96, phomemo.headDots)
        assertEquals(12, phomemo.bytesPerColumn)
        assertEquals(4000, phomemo.maxLengthDots)
        assertEquals(listOf(12, 14, 15), phomemo.tapeWidthsMm)
    }
}
