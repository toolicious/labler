package io.github.toolicious.labler.printer

import kotlin.math.roundToInt

/**
 * Procedural geometry test pattern for the print test. It makes orientation,
 * mirroring, cropping and dimensional accuracy clearly recognizable on the printout:
 * - 2-px border on all four edges (cropping test)
 * - filled 12x12 square at top left at (8,8) (corner anchor)
 * - diagonal from the top left corner down across the full head height (mirroring test)
 * - arrow in +X direction at half height (print direction test)
 * - ticks at the top edge: every 5 mm short, every 10 mm long (scale test)
 *
 * Verified on the device (M1): column x=0 leaves the printer first (leading edge),
 * the arrow (+X) points toward the tear-off edge at the slot. Tick spacings are exact,
 * the die-cut finish transports to the label gap.
 */
object TestPattern {

    fun create(
        geometry: HeadGeometry,
        lengthDots: Int = defaultLengthDots(geometry),
        calibrationSpan: Int = calibrationSpanDots(geometry),
    ): MonoImage {
        val img = MonoImage.blank(lengthDots, geometry.headDots)
        val w = lengthDots
        val h = geometry.headDots

        // Border, 2 px thick
        for (x in 0 until w) {
            img.setBlack(x, 0); img.setBlack(x, 1)
            img.setBlack(x, h - 2); img.setBlack(x, h - 1)
        }
        for (y in 0 until h) {
            img.setBlack(0, y); img.setBlack(1, y)
            img.setBlack(w - 2, y); img.setBlack(w - 1, y)
        }

        // Diagonal (2 px thick) from top left to bottom right within the head square
        for (d in 0 until h) {
            img.setBlack(d, d)
            img.setBlack(d + 1, d)
        }

        // Filled 12x12 square at top left
        for (x in 8 until 20) {
            for (y in 8 until 20) img.setBlack(x, y)
        }

        // Arrow in +X direction at half height
        val cy = h / 2
        for (x in 120..208) {
            img.setBlack(x, cy); img.setBlack(x, cy + 1)
        }
        for (i in 0..12) {
            img.setBlack(208 - i, cy - i)
            img.setBlack(208 - i, cy + 1 + i)
        }

        // Two marks at the bottom edge, a stated distance apart. The ticks above are a scale
        // to read off; these are a pair to put a caliper on, which is a different job and needs
        // marks of its own rather than a count of ticks.
        val (left, right) = calibrationMarks(lengthDots, calibrationSpan)
        for (y in h - h / 4 until h - 2) {
            img.setBlack(left, y); img.setBlack(left + 1, y)
            img.setBlack(right, y); img.setBlack(right + 1, y)
        }

        // Ticks at the top edge: every 5 mm short, every 10 mm long
        for ((mm, x) in tickColumns(geometry, lengthDots)) {
            val len = if (mm % 10 == 0) 16 else 8
            for (y in 2 until 2 + len) {
                img.setBlack(x, y); img.setBlack(x + 1, y)
            }
        }
        return img
    }

    /**
     * Millimeter mark and its column, for every tick [create] draws.
     *
     * The ticks follow millimeters, not a fixed number of dots, so a corrected feed shows up
     * on the tape as ticks that really are [TICK_STEP_MM] apart. How many of them fit therefore
     * varies, which is why they are a scale to read off and not the thing to measure.
     */
    fun tickColumns(geometry: HeadGeometry, lengthDots: Int = defaultLengthDots(geometry)): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var mm = TICK_STEP_MM
        while (true) {
            val x = (mm * geometry.dotsPerMm).roundToInt()
            if (x >= lengthDots - 2) break
            out += mm to x
            mm += TICK_STEP_MM
        }
        return out
    }

    /**
     * How long the pattern is, in dots. Worked out from millimeters rather than fixed, because
     * the pattern is meant to fit a [LENGTH_MM] die-cut label. As a dot count it stopped fitting
     * the moment the feed resolution was corrected, and nobody noticed because the app was
     * quoting the same wrong number back to itself.
     */
    fun defaultLengthDots(geometry: HeadGeometry): Int = geometry.mmToDots(LENGTH_MM)

    /**
     * Dots between the two calibration marks, which is [CALIBRATION_MM] on the grid the family
     * declares.
     *
     * Always the declared grid, never a corrected one: the marks have to keep their distance
     * across corrections, otherwise entering the same reading a second time walks the result a
     * little further instead of leaving it alone.
     */
    fun calibrationSpanDots(declared: HeadGeometry): Int = declared.mmToDots(CALIBRATION_MM)

    /** Columns of the two calibration marks, centered on the pattern. */
    fun calibrationMarks(lengthDots: Int, calibrationSpan: Int): Pair<Int, Int> {
        val left = ((lengthDots - calibrationSpan) / 2).coerceAtLeast(2)
        return left to left + calibrationSpan
    }

    /** How far a correction may stray from the declared resolution before it is a typo. */
    const val PLAUSIBLE_FACTOR = 2f

    /** Length of the pattern, matching the die-cut label it is meant to be printed on. */
    const val LENGTH_MM = 40

    /** What the two marks are apart, and so the distance the user is asked to measure. */
    const val CALIBRATION_MM = 30

    const val TICK_STEP_MM = 5
}
