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

    fun create(geometry: HeadGeometry, lengthDots: Int = DEFAULT_LENGTH_DOTS): MonoImage {
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

        // Two marks at the bottom edge whose distance is fixed in dots. The ticks above
        // follow millimetres and therefore shift with every correction, which makes them a
        // good check and a useless reference; these are the reference.
        val (left, right) = calibrationMarks(lengthDots)
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
     * Millimetre mark and its column, for every tick [create] draws.
     *
     * The ticks follow millimetres, not a fixed number of dots, so a corrected feed shows up
     * on the tape as ticks that really are [TICK_STEP_MM] apart. A coarser grid then fits one
     * more of them into the same label, which is why the distance to measure is quoted as a
     * tick count rather than as a constant.
     */
    fun tickColumns(geometry: HeadGeometry, lengthDots: Int = DEFAULT_LENGTH_DOTS): List<Pair<Int, Int>> {
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
     * Dots between the first and the last tick, which is the distance someone measures with a
     * ruler to work out what the printer really feeds. Null where fewer than two ticks fit.
     */
    fun tickSpanDots(geometry: HeadGeometry, lengthDots: Int = DEFAULT_LENGTH_DOTS): Int? {
        val ticks = tickColumns(geometry, lengthDots)
        return if (ticks.size < 2) null else ticks.last().second - ticks.first().second
    }

    /**
     * Columns of the two calibration marks. Fixed in dots and independent of any
     * resolution, so the distance someone measures is the same before and after a
     * correction and entering the same reading twice changes nothing.
     */
    fun calibrationMarks(lengthDots: Int = DEFAULT_LENGTH_DOTS): Pair<Int, Int> =
        CALIBRATION_INSET to lengthDots - CALIBRATION_INSET

    /** Dots between the two calibration marks, measured from the same edge of each. */
    fun calibrationSpanDots(lengthDots: Int = DEFAULT_LENGTH_DOTS): Int =
        lengthDots - 2 * CALIBRATION_INSET

    /** How far a correction may stray from the declared resolution before it is a typo. */
    const val PLAUSIBLE_FACTOR = 2f

    /**
     * Where the calibration marks sit from either end. Chosen so the distance between them
     * is the 240 dots the first and last tick used to span at 8 dots per millimetre, which
     * keeps a measurement taken before these marks existed valid.
     */
    private const val CALIBRATION_INSET = 40

    const val DEFAULT_LENGTH_DOTS = 320
    const val TICK_STEP_MM = 5
}
