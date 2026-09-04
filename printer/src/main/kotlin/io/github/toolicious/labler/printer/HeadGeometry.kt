package io.github.toolicious.labler.printer

import kotlin.math.roundToInt

/**
 * Print geometry of one printer family: everything the editor, the renderer and the job builder
 * need in order to map a label given in millimeters onto printer dots.
 *
 * A label carries the family it was designed for, so a design keeps the geometry it was drawn
 * with instead of following whichever printer happens to be connected.
 *
 * The two axes are not the same grid. Across the tape the pitch belongs to the head and is fixed
 * in the part; along it the tape advances by whatever motor and gearing produce, which on one
 * family is two per cent off that and on another exactly double it. A label is therefore laid out
 * on the square grid of [headDotsPerMm] and only turned into columns when it is rastered, so a
 * design stays free of the feed and a correction moves nothing the user placed.
 */
data class HeadGeometry(
    /** Dots across the print head, which is the fixed height of every rendered label. */
    val headDots: Int,
    /** Dots per millimeter across the tape: the pitch of the head, and the grid a label is drawn on. */
    val headDotsPerMm: Float,
    /** Dots per millimeter along the tape: how far the feed really advances per printed column. */
    val feedDotsPerMm: Float,
    /** Bytes one raster column occupies on the wire. */
    val bytesPerColumn: Int,
    /** Bounds for a label length in mm, for a fixed value as well as an auto-grown one. */
    val minLengthMm: Int,
    val maxLengthMm: Int,
    /** Bounds for the tape width in mm. */
    val minTapeMm: Int,
    val maxTapeMm: Int,
    /** Tape widths available as cartridges. */
    val tapeWidthsMm: List<Int>,
    /** Commercially available die-cut labels (tape width x length in mm), empty if none. */
    val diecutPresets: List<Pair<Int, Int>>,
) {

    val maxLengthDots: Int get() = mmToDots(maxLengthMm)

    /**
     * [feedDotsPerMm] in the unit the manufacturers print on their boxes, so a measured value
     * can be held against a data sheet without doing the arithmetic first. The feed is the axis
     * a user gets to correct, so it is the one quoted.
     */
    val dpi: Float get() = feedDotsPerMm * MM_PER_INCH

    /**
     * How much wider the printer's column grid is than the square one a label is laid out on.
     * One on a machine whose two axes agree, and nothing else does the stretching.
     */
    val feedAspect: Float get() = feedDotsPerMm / headDotsPerMm

    /** Whole millimeters to layout dots, which are square. */
    fun mmToDots(mm: Int): Int = (mm * headDotsPerMm).roundToInt()

    /**
     * Layout dots back to whole millimeters, for putting a length in front of someone. Rounds
     * rather than truncates, because the grid is not a whole number of dots per millimeter:
     * cutting off turned a 20 mm label into "19 mm" on a family where it came to 19.94.
     */
    fun dotsToMm(dots: Int): Int = (dots / headDotsPerMm).roundToInt()

    /** Whole millimeters to printer columns, for a raster built on the printer's own grid. */
    fun mmToColumns(mm: Int): Int = (mm * feedDotsPerMm).roundToInt()

    /** Printer columns back to whole millimeters. */
    fun columnsToMm(columns: Int): Int = (columns / feedDotsPerMm).roundToInt()

    /** A length laid out in square dots, as the columns the printer is actually sent. */
    fun layoutToColumns(dots: Int): Int = (dots * feedAspect).roundToInt()

    companion object {
        const val MM_PER_INCH = 25.4f
    }
}
