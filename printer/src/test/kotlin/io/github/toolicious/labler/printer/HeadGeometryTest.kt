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
    fun `the grid is the feed two printers measured, not the pitch the head is sold with`() {
        assertEquals(200f, phomemo.dpi, 0.001f)
        assertEquals(7.874f, phomemo.dotsPerMm, 0.001f)
    }

    @Test
    fun `millimeters to dots, spelled out`() {
        assertEquals(0, phomemo.mmToDots(0))
        assertEquals(79, phomemo.mmToDots(10))
        // The die-cut label the test pattern is meant for.
        assertEquals(315, phomemo.mmToDots(40))
        assertEquals(1181, phomemo.mmToDots(150))
        // The ruler case: 38 dots less than the old grid asked for, which is the 4.8 mm a
        // 300 mm scale used to come out too long.
        assertEquals(2362, phomemo.mmToDots(300))
        assertEquals(3937, phomemo.mmToDots(500))
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
        assertEquals(3937, phomemo.maxLengthDots)
        assertEquals(listOf(12, 14, 15), phomemo.tapeWidthsMm)
    }
}
