package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the millimeter to dot conversion of the Phomemo family. A mismatch here moves every
 * element of every label, so the expected numbers are spelled out instead of being recomputed
 * from the same formula the code uses.
 */
class HeadGeometryTest {

    private val phomemo = PhomemoProtocol.geometry

    @Test
    fun `the two axes are different grids`() {
        // The head is the 203 dpi it is sold with, the feed the 200 two printers measured.
        assertEquals(8f, phomemo.headDotsPerMm, 0.001f)
        assertEquals(7.874f, phomemo.feedDotsPerMm, 0.001f)
        assertEquals(200f, phomemo.dpi, 0.001f)
        assertEquals(0.984f, phomemo.feedAspect, 0.001f)
    }

    @Test
    fun `a label is laid out on the square grid of the head`() {
        assertEquals(0, phomemo.mmToDots(0))
        assertEquals(80, phomemo.mmToDots(10))
        assertEquals(320, phomemo.mmToDots(40))
        assertEquals(1200, phomemo.mmToDots(150))
        assertEquals(2400, phomemo.mmToDots(300))
        assertEquals(4000, phomemo.mmToDots(500))
    }

    @Test
    fun `and rastered onto the columns the feed really covers`() {
        // The die-cut label the test pattern is meant for: 320 square dots, 315 columns.
        assertEquals(315, phomemo.layoutToColumns(320))
        assertEquals(315, phomemo.mmToColumns(40))
        assertEquals(40, phomemo.columnsToMm(315))
        // The ruler case, where the old grid asked for 38 columns too many.
        assertEquals(2362, phomemo.mmToColumns(300))
    }

    @Test
    fun `a leading edge pulled into the negative converts the same way`() {
        for (mm in -500..0) {
            assertEquals(-phomemo.mmToDots(-mm), phomemo.mmToDots(mm), "at $mm mm")
        }
    }

    @Test
    fun `a length survives the trip to dots and back`() {
        for (mm in 0..phomemo.maxLengthMm) {
            assertEquals(mm, phomemo.dotsToMm(phomemo.mmToDots(mm)), "at $mm mm")
        }
    }

    @Test
    fun `derived bounds follow the family`() {
        assertEquals(96, phomemo.headDots)
        assertEquals(12, phomemo.bytesPerColumn)
        assertEquals(4000, phomemo.maxLengthDots)
        assertEquals(listOf(12, 14, 15), phomemo.tapeWidthsMm)
    }
}
