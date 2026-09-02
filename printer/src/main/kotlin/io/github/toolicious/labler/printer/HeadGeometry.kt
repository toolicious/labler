package io.github.toolicious.labler.printer

import kotlin.math.roundToInt

/**
 * Print geometry of one printer family: everything the editor, the renderer and the job builder
 * need in order to map a label given in millimeters onto printer dots.
 *
 * A label carries the family it was designed for, so a design keeps the geometry it was drawn
 * with instead of following whichever printer happens to be connected.
 */
data class HeadGeometry(
    /** Dots across the print head, which is the fixed height of every rendered label. */
    val headDots: Int,
    /**
     * Dots per millimeter along the tape. Not a whole number on every family, hence the Float.
     * Where it is one, the conversions below reproduce the plain integer arithmetic exactly.
     */
    val dotsPerMm: Float,
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
     * [dotsPerMm] in the unit the manufacturers print on their boxes, so a measured value
     * can be held against a data sheet without doing the arithmetic first.
     */
    val dpi: Float get() = dotsPerMm * MM_PER_INCH

    /** Whole millimeters to dots. */
    fun mmToDots(mm: Int): Int = (mm * dotsPerMm).roundToInt()

    /** Dots back to whole millimeters, cutting off what does not fill one. */
    fun dotsToMm(dots: Int): Int = (dots / dotsPerMm).toInt()

    companion object {
        const val MM_PER_INCH = 25.4f
    }
}
