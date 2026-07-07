package io.github.toolicious.labler.printer.dither

import kotlin.math.abs
import kotlin.math.pow

/**
 * Canny-style edge detector for photos. Unlike the region-based [Outline] (which finds boundaries
 * between flat color areas and leaves scattered dots on photos), this produces clean, connected
 * line art:
 *
 * 1. light Gaussian blur to calm noise,
 * 2. Sobel gradient,
 * 3. non-maximum suppression, which thins each edge to a 1 px ridge,
 * 4. double-threshold hysteresis: strong edges are always drawn; weak edges only when connected to
 *    a strong one, so isolated noise pixels are dropped.
 *
 * Magnitudes are kept squared throughout (comparisons and percentiles are order-preserving), which
 * avoids a per-pixel square root; the low threshold is therefore 0.16 = 0.4^2 of the high one.
 */
object Canny {

    /**
     * Squared-gradient floor; keeps a near-blank image empty. Kept low so it only bites on very
     * low-contrast images, otherwise it would clamp the high end of the slider (the threshold is
     * relative to the strongest edge, which for a moderate-contrast photo can sit below a high floor).
     */
    private const val NOISE_FLOOR = 50f

    fun detect(
        argb: IntArray,
        isGlyph: BooleanArray,
        width: Int,
        height: Int,
        sensitivity: Int,
        thickness: Int,
        smooth: Boolean = false,
    ): BooleanArray {
        val n = width * height
        // Luminance; non-glyph pixels act as a white background so a symbol's silhouette is an edge.
        val lum = FloatArray(n) { i ->
            if (isGlyph[i]) {
                val c = argb[i]
                0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
            } else {
                255f
            }
        }
        val blur = gaussian3(lum, width, height)

        val mag = FloatArray(n) // squared gradient magnitude
        val dir = IntArray(n)   // 0 = -, 1 = /, 2 = |, 3 = \
        for (y in 0 until height) {
            for (x in 0 until width) {
                val gx = sobelX(blur, x, y, width, height)
                val gy = sobelY(blur, x, y, width, height)
                val i = y * width + x
                mag[i] = gx * gx + gy * gy
                dir[i] = quantizeDir(gx, gy)
            }
        }

        // Non-maximum suppression: keep a pixel only if it is a ridge along the gradient normal.
        val nms = FloatArray(n)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val m = mag[i]
                if (m <= 0f) continue
                val dx: Int
                val dy: Int
                when (dir[i]) {
                    0 -> { dx = 1; dy = 0 }
                    1 -> { dx = 1; dy = -1 }
                    2 -> { dx = 0; dy = 1 }
                    else -> { dx = 1; dy = 1 }
                }
                if (m >= magAt(mag, x + dx, y + dy, width, height) &&
                    m >= magAt(mag, x - dx, y - dy, width, height)
                ) {
                    nms[i] = m
                }
            }
        }

        val ridge = ArrayList<Float>()
        for (i in 0 until n) if (isGlyph[i] && nms[i] > 0f) ridge.add(nms[i])
        val base = BooleanArray(n)
        if (ridge.isNotEmpty()) {
            ridge.sort()
            val s = sensitivity.coerceIn(0, 100)
            // Threshold RELATIVE to the strongest edges (99th percentile of the squared magnitudes),
            // not a mid-percentile of all ridges. Faint background texture is only a small fraction
            // of a real edge, so it stays below the threshold at every setting instead of flooding in
            // as the slider rises. Higher sensitivity lowers the fraction (finer real edges appear).
            val strongRef = ridge[((ridge.size - 1) * 0.99f).toInt().coerceIn(0, ridge.size - 1)]
            // Geometric drop (each slider step multiplies the threshold). The seed (high) threshold
            // falls to 0.3 % of the strongest edge; the low (hysteresis) threshold falls much
            // further, so near 100 faint edges CONNECTED to a strong one are kept while disconnected
            // background noise is still dropped.
            val hiFrac = 0.28f * (0.003f / 0.28f).pow(s / 100f) // seed: 0.28 -> 0.003 of the max
            val loRatio = 0.4f * (0.05f / 0.4f).pow(s / 100f)   // lo/hi ratio: 0.4 -> 0.05
            val hi = (strongRef * hiFrac).coerceAtLeast(NOISE_FLOOR)
            val lo = (hi * loRatio).coerceAtLeast(NOISE_FLOOR * 0.3f)

            val weak = BooleanArray(n)
            val stack = ArrayDeque<Int>()
            for (i in 0 until n) {
                if (!isGlyph[i]) continue
                when {
                    nms[i] >= hi -> { base[i] = true; stack.addLast(i) } // strong -> always an edge
                    nms[i] >= lo -> weak[i] = true
                }
            }
            // Hysteresis: flood from strong pixels into connected weak pixels (8-neighborhood).
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val cx = i % width
                val cy = i / width
                for (oy in -1..1) for (ox in -1..1) {
                    if (ox == 0 && oy == 0) continue
                    val nx = cx + ox
                    val ny = cy + oy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val j = ny * width + nx
                    if (weak[j] && !base[j]) {
                        base[j] = true
                        stack.addLast(j)
                    }
                }
            }
        }
        val cleaned = if (smooth) Morphology.smooth(base, width, height) else base
        return Outline.thicken(cleaned, isGlyph, width, height, thickness.coerceIn(1, 3))
    }

    private fun gaussian3(src: FloatArray, w: Int, h: Int): FloatArray {
        val tmp = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            val l = src[y * w + (x - 1).coerceAtLeast(0)]
            val c = src[y * w + x]
            val r = src[y * w + (x + 1).coerceAtMost(w - 1)]
            tmp[y * w + x] = (l + 2f * c + r) / 4f
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            val u = tmp[(y - 1).coerceAtLeast(0) * w + x]
            val c = tmp[y * w + x]
            val d = tmp[(y + 1).coerceAtMost(h - 1) * w + x]
            out[y * w + x] = (u + 2f * c + d) / 4f
        }
        return out
    }

    private fun sobelX(p: FloatArray, x: Int, y: Int, w: Int, h: Int): Float {
        fun v(xx: Int, yy: Int) = p[yy.coerceIn(0, h - 1) * w + xx.coerceIn(0, w - 1)]
        return (v(x + 1, y - 1) + 2f * v(x + 1, y) + v(x + 1, y + 1)) -
            (v(x - 1, y - 1) + 2f * v(x - 1, y) + v(x - 1, y + 1))
    }

    private fun sobelY(p: FloatArray, x: Int, y: Int, w: Int, h: Int): Float {
        fun v(xx: Int, yy: Int) = p[yy.coerceIn(0, h - 1) * w + xx.coerceIn(0, w - 1)]
        return (v(x - 1, y + 1) + 2f * v(x, y + 1) + v(x + 1, y + 1)) -
            (v(x - 1, y - 1) + 2f * v(x, y - 1) + v(x + 1, y - 1))
    }

    private fun magAt(mag: FloatArray, x: Int, y: Int, w: Int, h: Int): Float =
        if (x < 0 || y < 0 || x >= w || y >= h) 0f else mag[y * w + x]

    // tan(22.5) = 0.4142, tan(67.5) = 2.4142. y grows downward, so equal sign = "\" diagonal.
    private fun quantizeDir(gx: Float, gy: Float): Int {
        val ax = abs(gx)
        val ay = abs(gy)
        return when {
            ay <= 0.4142f * ax -> 0
            ay >= 2.4142f * ax -> 2
            (gx > 0f) == (gy > 0f) -> 3
            else -> 1
        }
    }
}
