package io.github.toolicious.labler.printer

import kotlin.math.roundToInt

/**
 * Procedural geometry test pattern for the print test. It makes orientation,
 * mirroring, cropping and dimensional accuracy clearly recognizable on the printout:
 * - border on all four edges (cropping test)
 * - hairline frame along the outer edges of that square, which lands on the label even
 *   on a print where the border does not
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

        // Border. The two edges running along the tape keep the same two elements of the head
        // firing for every column of the print, so they run hot and come out fatter than the two
        // that are a single short pulse across the whole head. The upright pair gets a dot more
        // to even that out on the tape; the extra dot goes inwards, so the outer size of the
        // frame is the same either way.
        for (x in 0 until w) {
            for (d in 0 until BORDER_DOTS) {
                img.setBlack(x, d); img.setBlack(x, h - 1 - d)
            }
        }
        for (y in 0 until h) {
            for (d in 0 until UPRIGHT_BORDER_DOTS) {
                img.setBlack(d, y); img.setBlack(w - 1 - d, y)
            }
        }

        // A hairline frame, one dot thick, running along the outer edges of the corner square
        // so the two line up instead of crossing. The border only fits when the tape sits
        // exactly right, so on a print where it runs off the label there is nothing left to
        // measure against. This one lands whatever happens, which turns the gap between it and
        // the label edge into a number rather than a guess.
        if (h > 2 * FRAME_INSET_Y + 2 && w > 2 * FRAME_INSET_X + 2) {
            for (x in FRAME_INSET_X until w - FRAME_INSET_X) {
                img.setBlack(x, FRAME_INSET_Y); img.setBlack(x, h - 1 - FRAME_INSET_Y)
            }
            for (y in FRAME_INSET_Y until h - FRAME_INSET_Y) {
                img.setBlack(FRAME_INSET_X, y); img.setBlack(w - 1 - FRAME_INSET_X, y)
            }
        }

        // Diagonal (2 px thick) from top left to bottom right within the head square
        for (d in 0 until h) {
            img.setBlack(d, d)
            img.setBlack(d + 1, d)
        }

        // Filled square in the top left corner, its outer edges on the hairline frame
        for (x in FRAME_INSET_X until FRAME_INSET_X + SQUARE_DOTS) {
            for (y in FRAME_INSET_Y until FRAME_INSET_Y + SQUARE_DOTS) img.setBlack(x, y)
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

    /** Thickness of the two border edges that run along the tape. */
    const val BORDER_DOTS = 2

    /**
     * Thickness of the two border edges that run across it, one dot more so that both pairs
     * come off the printer looking the same. On screen they look too thick for it, which is the
     * price of a preview that knows nothing about a print head warming up.
     */
    const val UPRIGHT_BORDER_DOTS = 3

    /**
     * Clear dots between the border and the hairline frame, which is what the eye compares.
     * Held equal on all four sides rather than the distance to the edge of the print, because
     * the upright border is a dot thicker and would otherwise eat into its own side of the gap.
     */
    const val FRAME_GAP_DOTS = 6

    /**
     * Where the hairline frame runs, and with it the outer edges of the corner square. One pair
     * of numbers for both, so the square can only ever sit exactly in the corner of the frame.
     */
    const val FRAME_INSET_X = UPRIGHT_BORDER_DOTS + FRAME_GAP_DOTS
    const val FRAME_INSET_Y = BORDER_DOTS + FRAME_GAP_DOTS

    /** Side of the corner square. */
    const val SQUARE_DOTS = 12

    /** How far a correction may stray from the declared resolution before it is a typo. */
    const val PLAUSIBLE_FACTOR = 2f

    /**
     * Length of the pattern, which is exactly the die-cut label it goes on. That leaves no room
     * for a placement error, and that is the point rather than an oversight: the border can only
     * fit if the tape sits right, so whichever end of it falls off the label says which way the
     * tape is out. Shortening the pattern would hide the one thing it is there to show.
     */
    const val LENGTH_MM = 40

    /** What the two marks are apart, and so the distance the user is asked to measure. */
    const val CALIBRATION_MM = 30

    const val TICK_STEP_MM = 5
}
