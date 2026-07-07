package io.github.toolicious.labler.printer

/**
 * Procedural geometry test pattern for the M1 print test. It makes orientation,
 * mirroring, cropping and dimensional accuracy clearly recognizable on the printout:
 * - 2-px border on all four edges (cropping test)
 * - filled 12x12 square at top left at (8,8) (corner anchor)
 * - diagonal from (0,0) to (95,95) (mirroring test)
 * - arrow in +X direction at half height (print direction test)
 * - ticks at the top edge: every 40 dots short (= 5 mm), every 80 dots long (= 10 mm)
 *
 * Verified on the device (M1): column x=0 leaves the printer first (leading edge),
 * the arrow (+X) points toward the tear-off edge at the slot. Tick spacings are exact
 * (8 dots/mm), the die-cut finish transports to the label gap.
 */
object TestPattern {

    fun create(lengthDots: Int = 320): MonoImage {
        val img = MonoImage.blank(lengthDots)
        val w = lengthDots
        val h = Protocol.HEAD_DOTS

        // Border, 2 px thick
        for (x in 0 until w) {
            img.setBlack(x, 0); img.setBlack(x, 1)
            img.setBlack(x, h - 2); img.setBlack(x, h - 1)
        }
        for (y in 0 until h) {
            img.setBlack(0, y); img.setBlack(1, y)
            img.setBlack(w - 2, y); img.setBlack(w - 1, y)
        }

        // Diagonal (2 px thick) from top left to bottom right within the 96 square
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

        // Ticks at the top edge: every 40 dots (5 mm) short, every 80 dots (10 mm) long
        var x = 40
        while (x < w - 2) {
            val len = if (x % 80 == 0) 16 else 8
            for (y in 2 until 2 + len) {
                img.setBlack(x, y); img.setBlack(x + 1, y)
            }
            x += 40
        }
        return img
    }
}
