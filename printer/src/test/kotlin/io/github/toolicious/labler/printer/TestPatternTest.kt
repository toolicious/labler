package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tick marks are the one part of the pattern someone measures with a ruler, so where they sit
 * has to be beyond doubt: a deviation on the tape is then the printer's, not the pattern's.
 */
class TestPatternTest {

    private val pattern = TestPattern.create(PhomemoProtocol.geometry)

    /** Row 5 is inside every tick, below the border and above the square. */
    private fun tickColumns(row: Int): List<Int> =
        (2 until 318).filter { pattern.isBlack(it, row) }
            // The diagonal runs through the upper rows as well and is not a tick.
            .filterNot { it == row || it == row + 1 }

    @Test
    fun `short ticks sit every 40 dots, which is every 5 mm at 8 dots per mm`() {
        assertEquals(
            listOf(40, 41, 80, 81, 120, 121, 160, 161, 200, 201, 240, 241, 280, 281),
            tickColumns(5),
        )
    }

    @Test
    fun `only every second tick reaches down to row 12, at 10 mm spacing`() {
        // The filled square covers 8..19 in these rows and is not a tick either.
        assertEquals(
            listOf(80, 81, 160, 161, 240, 241),
            tickColumns(12).filterNot { it in 8..19 },
        )
    }

    @Test
    fun `the pattern fills the length it was asked for`() {
        assertEquals(320, pattern.width)
        assertEquals(PhomemoProtocol.HEAD_DOTS, pattern.height)
        // Border on all four edges, so the printed strip is exactly the label.
        assertTrue(pattern.isBlack(0, 0))
        assertTrue(pattern.isBlack(319, 95))
        assertFalse(pattern.isBlack(160, 40))
    }

    @Test
    fun `a coarser dot grid moves the ticks but keeps them 5 mm apart`() {
        val dymo = TestPattern.create(DymoProtocol.DEFAULT.geometry, lengthDots = 200)
        // 160 dpi is 6.2992 dots per mm, so 5 mm lands on 31 dots and 10 mm on 63.
        assertTrue(dymo.isBlack(31, 5))
        assertTrue(dymo.isBlack(63, 5))
        assertFalse(dymo.isBlack(40, 5))
    }

    @Test
    fun `the calibration marks hold their distance whatever the resolution is`() {
        val span = TestPattern.calibrationSpanDots()
        assertEquals(240, span)
        // A coarser grid moves every millimeter tick, and leaves the two marks exactly where
        // they were. That is what makes them something to measure against twice.
        val coarse = PhomemoProtocol.geometry.copy(dotsPerMm = 6f)
        assertEquals(span, TestPattern.calibrationSpanDots())
        assertTrue(TestPattern.tickColumns(coarse) != TestPattern.tickColumns(PhomemoProtocol.geometry))

        val (left, right) = TestPattern.calibrationMarks()
        listOf(PhomemoProtocol.geometry, coarse).forEach { geometry ->
            val drawn = TestPattern.create(geometry)
            val row = geometry.headDots - 4
            assertTrue(drawn.isBlack(left, row), "left mark at ${geometry.dotsPerMm} dots/mm")
            assertTrue(drawn.isBlack(right, row), "right mark at ${geometry.dotsPerMm} dots/mm")
        }
    }
}
