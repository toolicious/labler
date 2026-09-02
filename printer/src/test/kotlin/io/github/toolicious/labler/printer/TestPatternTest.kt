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
        (2 until pattern.width - 2).filter { pattern.isBlack(it, row) }
            // The diagonal runs through the upper rows as well and is not a tick.
            .filterNot { it == row || it == row + 1 }

    @Test
    fun `short ticks land on the dot each 5 mm step falls on`() {
        // 7.874 dots per mm, so 5 mm is 39.4 dots and the ticks cannot be evenly spaced.
        // Each is two dots wide, and the 40 mm one would land on the border.
        assertEquals(
            listOf(39, 40, 79, 80, 118, 119, 157, 158, 197, 198, 236, 237, 276, 277),
            tickColumns(5),
        )
    }

    @Test
    fun `only every second tick reaches down to row 12, at 10 mm spacing`() {
        // The filled square covers 8..19 in these rows and is not a tick either.
        assertEquals(
            listOf(79, 80, 157, 158, 236, 237),
            tickColumns(12).filterNot { it in 8..19 },
        )
    }

    @Test
    fun `the pattern fills the length it was asked for`() {
        assertEquals(315, pattern.width)
        assertEquals(PhomemoProtocol.HEAD_DOTS, pattern.height)
        // Border on all four edges, so the printed strip is exactly the label.
        assertTrue(pattern.isBlack(0, 0))
        assertTrue(pattern.isBlack(314, 95))
        assertFalse(pattern.isBlack(160, 40))
    }

    @Test
    fun `the pattern is the label it is meant for, not a dot count that used to match`() {
        val declared = PhomemoProtocol.geometry
        assertEquals(315, TestPattern.defaultLengthDots(declared))
        assertEquals(TestPattern.LENGTH_MM, declared.dotsToMm(TestPattern.defaultLengthDots(declared)))
        // A corrected feed wants a different number of dots for the same 40 mm, which is the
        // whole reason this is not a constant.
        val corrected = declared.copy(dotsPerMm = 7.8406f)
        assertEquals(314, TestPattern.defaultLengthDots(corrected))
        assertEquals(TestPattern.LENGTH_MM, corrected.dotsToMm(TestPattern.defaultLengthDots(corrected)))
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
    fun `the calibration marks hold their distance when the feed is corrected`() {
        val declared = PhomemoProtocol.geometry
        val span = TestPattern.calibrationSpanDots(declared)
        // 30 mm on the declared grid, which is the number the dialog quotes.
        assertEquals(236, span)

        // A correction moves every millimeter tick and leaves the two marks the same distance
        // apart. That is what makes them something to measure against twice.
        val corrected = declared.copy(dotsPerMm = 7.5f)
        assertTrue(TestPattern.tickColumns(corrected) != TestPattern.tickColumns(declared))

        listOf(declared, corrected).forEach { geometry ->
            val lengthDots = TestPattern.defaultLengthDots(geometry)
            val (left, right) = TestPattern.calibrationMarks(lengthDots, span)
            assertEquals(span, right - left, "span at ${geometry.dotsPerMm} dots/mm")
            val drawn = TestPattern.create(geometry, lengthDots, span)
            val row = geometry.headDots - 4
            assertTrue(drawn.isBlack(left, row), "left mark at ${geometry.dotsPerMm} dots/mm")
            assertTrue(drawn.isBlack(right, row), "right mark at ${geometry.dotsPerMm} dots/mm")
        }
    }
}
