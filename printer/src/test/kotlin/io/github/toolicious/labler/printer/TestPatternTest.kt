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
        (TestPattern.UPRIGHT_BORDER_DOTS until pattern.width - TestPattern.UPRIGHT_BORDER_DOTS)
            .filter { pattern.isBlack(it, row) }
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
        val square = TestPattern.FRAME_INSET_X until TestPattern.FRAME_INSET_X + TestPattern.SQUARE_DOTS
        val hairline = setOf(
            TestPattern.FRAME_INSET_X,
            pattern.width - 1 - TestPattern.FRAME_INSET_X,
        )
        assertEquals(
            listOf(79, 80, 157, 158, 236, 237),
            tickColumns(12).filterNot { it in square || it in hairline },
        )
    }

    @Test
    fun `the upright edges of the border carry a dot more than the flat ones`() {
        // Row 40 is clear of the square, the diagonal, the arrow and the ticks.
        assertTrue(pattern.isBlack(2, 40), "third column of the left border")
        assertFalse(pattern.isBlack(3, 40), "fourth column would be too much")
        assertTrue(pattern.isBlack(pattern.width - 3, 40), "third column of the right border")
        assertFalse(pattern.isBlack(pattern.width - 4, 40), "fourth column would be too much")
        // Column 160 is clear of everything but the border rows themselves.
        assertTrue(pattern.isBlack(160, 1), "second row of the top border")
        assertFalse(pattern.isBlack(160, 2), "third row would make the flat edges fat too")
        assertTrue(pattern.isBlack(160, pattern.height - 2), "second row of the bottom border")
        assertFalse(pattern.isBlack(160, pattern.height - 3), "third row would be too much")
    }

    @Test
    fun `a hairline frame runs along the outer edges of the corner square`() {
        val x = TestPattern.FRAME_INSET_X
        val y = TestPattern.FRAME_INSET_Y
        // Row 40 and column 160 are clear of everything else the pattern draws.
        assertTrue(pattern.isBlack(x, 40), "left hairline")
        assertTrue(pattern.isBlack(pattern.width - 1 - x, 40), "right hairline")
        assertTrue(pattern.isBlack(160, y), "top hairline")
        assertTrue(pattern.isBlack(160, pattern.height - 1 - y), "bottom hairline")
        // One dot and no more.
        assertFalse(pattern.isBlack(x + 1, 40))
        assertFalse(pattern.isBlack(160, y + 1))
        // And the square begins on it rather than beside it.
        assertTrue(pattern.isBlack(x + TestPattern.SQUARE_DOTS - 1, y))
        assertFalse(pattern.isBlack(x + TestPattern.SQUARE_DOTS, y + 4))
    }

    @Test
    fun `the gap between border and hairline is the same on all four sides`() {
        val gap = TestPattern.FRAME_GAP_DOTS
        // Counted as the clear dots between the two, which is what the eye compares.
        assertEquals(gap, TestPattern.FRAME_INSET_Y - TestPattern.BORDER_DOTS, "top and bottom")
        assertEquals(gap, TestPattern.FRAME_INSET_X - TestPattern.UPRIGHT_BORDER_DOTS, "left and right")
        // Row 40 and column 160 again, so nothing else is in the way.
        assertFalse(pattern.isBlack(TestPattern.UPRIGHT_BORDER_DOTS, 40), "left gap starts clear")
        assertFalse(pattern.isBlack(TestPattern.FRAME_INSET_X - 1, 40), "left gap ends clear")
        assertFalse(pattern.isBlack(160, TestPattern.BORDER_DOTS), "top gap starts clear")
        assertFalse(pattern.isBlack(160, TestPattern.FRAME_INSET_Y - 1), "top gap ends clear")
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
