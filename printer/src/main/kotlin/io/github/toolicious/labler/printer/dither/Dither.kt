package io.github.toolicious.labler.printer.dither

/** Methods for converting grayscale content (images, icons) to 1-bit. */
enum class DitherMode { OUTLINE, THRESHOLD, FLOYD_STEINBERG, ATKINSON }

/**
 * Input: luminance 0..255 per pixel (row-major). Result: true = black.
 * All implementations are deterministic (testable with fixed inputs).
 */
interface Ditherer {
    fun dither(gray: FloatArray, width: Int, height: Int): BooleanArray

    companion object {
        fun of(mode: DitherMode): Ditherer = when (mode) {
            DitherMode.THRESHOLD -> ThresholdDitherer
            DitherMode.FLOYD_STEINBERG -> FloydSteinbergDitherer
            DitherMode.ATKINSON -> AtkinsonDitherer
            // OUTLINE works on the original pixels and is produced directly via Outline.trace in the
            // renderer; this is just a harmless fallback in case of() is called with it.
            DitherMode.OUTLINE -> ThresholdDitherer
        }
    }
}

/** Hard threshold at 128. */
object ThresholdDitherer : Ditherer {
    override fun dither(gray: FloatArray, width: Int, height: Int): BooleanArray =
        BooleanArray(gray.size) { gray[it] < 128f }
}

/** Classic error diffusion 7/16, 3/16, 5/16, 1/16. */
object FloydSteinbergDitherer : Ditherer {
    override fun dither(gray: FloatArray, width: Int, height: Int): BooleanArray {
        val buf = gray.copyOf()
        val black = BooleanArray(buf.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val old = buf[i]
                val isBlack = old < 128f
                black[i] = isBlack
                val err = old - if (isBlack) 0f else 255f
                if (x + 1 < width) buf[i + 1] += err * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) buf[i + width - 1] += err * 3f / 16f
                    buf[i + width] += err * 5f / 16f
                    if (x + 1 < width) buf[i + width + 1] += err * 1f / 16f
                }
            }
        }
        return black
    }
}

/** Atkinson: 6 neighbors each get 1/8 of the error, looks higher in contrast (good for icons). */
object AtkinsonDitherer : Ditherer {
    override fun dither(gray: FloatArray, width: Int, height: Int): BooleanArray {
        val buf = gray.copyOf()
        val black = BooleanArray(buf.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val old = buf[i]
                val isBlack = old < 128f
                black[i] = isBlack
                val err = (old - if (isBlack) 0f else 255f) / 8f
                spread(buf, width, height, x + 1, y, err)
                spread(buf, width, height, x + 2, y, err)
                spread(buf, width, height, x - 1, y + 1, err)
                spread(buf, width, height, x, y + 1, err)
                spread(buf, width, height, x + 1, y + 1, err)
                spread(buf, width, height, x, y + 2, err)
            }
        }
        return black
    }

    private fun spread(buf: FloatArray, width: Int, height: Int, x: Int, y: Int, err: Float) {
        if (x in 0 until width && y in 0 until height) buf[y * width + x] += err
    }
}
