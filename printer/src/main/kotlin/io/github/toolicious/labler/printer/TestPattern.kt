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

    fun create(geometry: HeadGeometry, lengthDots: Int = 320): MonoImage {
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

        // Ticks at the top edge: every 5 mm short, every 10 mm long
        var mm = 5
        while (true) {
            val x = (mm * geometry.dotsPerMm).roundToInt()
            if (x >= w - 2) break
            val len = if (mm % 10 == 0) 16 else 8
            for (y in 2 until 2 + len) {
                img.setBlack(x, y); img.setBlack(x + 1, y)
            }
            mm += 5
        }
        return img
    }
}
