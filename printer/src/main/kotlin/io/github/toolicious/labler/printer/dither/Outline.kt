package io.github.toolicious.labler.printer.dither

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Which edge-detection method the outline renderer uses. LINES is region-based (color boundaries,
 * see [Outline]), best for flat graphics; CANNY is a gradient edge detector (see [Canny]), best for
 * photos.
 */
enum class OutlineMethod { LINES, CANNY }

/**
 * Contour/outline mode: Instead of filling or dithering areas, only the edges of the
 * graphic are drawn as lines (like a line drawing). Thin lines survive thermal printing
 * unchanged, so the print matches the preview.
 *
 * Edges are detected via the COLOR difference to the neighbor (largest channel difference), so
 * that colored transitions with little brightness difference are also detected. Only the
 * "inkier" side of an edge is marked (darker/more colorful, farther from white), so that
 * THIN lines are taken over 1:1 and not doubled on both sides.
 *
 * The sensitivity controls, rank-based, what fraction of the strongest edges is drawn.
 * Below [SILHOUETTE_BELOW] only the outer silhouette is shown (inner edges off). Above that,
 * the detail fraction is distributed over a concave curve ([DETAIL_GAMMA]), so that the useful
 * (detail-rich) range takes up a larger part of the slider. Monotonic: higher = more lines.
 */
object Outline {

    /** Below this, a color jump counts as noise and never becomes a line. */
    private const val FLOOR = 3f

    /** Below this sensitivity value, only the silhouette, no inner edges. */
    private const val SILHOUETTE_BELOW = 10

    /**
     * Reveal curve for the top-k selection. 1 = linear (equal number of edges added per slider
     * step, the smoothest even response); < 1 front-loads the reveal, > 1 back-loads it.
     */
    private const val DETAIL_GAMMA = 1.0f

    /**
     * @param argb        ARGB pixels (row-major); only glyph pixels are evaluated.
     * @param isGlyph     true = pixel belongs to the graphic (otherwise background).
     * @param sensitivity 0..100. Higher = weaker color edges also become lines.
     * @param thickness   line width 1..3 (thickened via dilation).
     * @param borderIsBackground true = the image edge counts as background, so an all-glyph
     *        graphic gets an outer silhouette (correct for symbols/emoji). false = no outline
     *        around the outer edge (correct for full-frame images).
     * @return true = black contour line.
     */
    fun trace(
        argb: IntArray,
        isGlyph: BooleanArray,
        width: Int,
        height: Int,
        sensitivity: Int,
        thickness: Int,
        borderIsBackground: Boolean = true,
        smooth: Boolean = false,
    ): BooleanArray {
        val n = argb.size
        val mag = FloatArray(n)
        val silhouette = BooleanArray(n)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (!isGlyph[i]) continue
                if (touchesBackground(isGlyph, x, y, width, height, borderIsBackground)) silhouette[i] = true
                mag[i] = maxInkEdge(argb, isGlyph, x, y, width, height)
            }
        }
        // A strength threshold, so a whole edge (uniform strength along its run) appears at once as a
        // clean line or not at all. Detects color boundaries, so equal-brightness color changes are
        // found too. Best for flat graphics; photos should use the Canny method.
        val s = sensitivity.coerceIn(0, 100)
        val edges = mag.filter { it > FLOOR }.sorted()
        val threshold = if (edges.isEmpty() || s < SILHOUETTE_BELOW) {
            Float.MAX_VALUE // silhouette only
        } else {
            val u = (s - SILHOUETTE_BELOW) / (100f - SILHOUETTE_BELOW) // 0..1
            val f = u.pow(DETAIL_GAMMA)
            val idx = ((1f - f) * (edges.size - 1)).roundToInt().coerceIn(0, edges.size - 1)
            edges[idx]
        }
        val base = BooleanArray(n) { silhouette[it] || mag[it] >= threshold }
        val cleaned = if (smooth) Morphology.smooth(base, width, height) else base
        return thicken(cleaned, isGlyph, width, height, thickness.coerceIn(1, 3))
    }

    /**
     * Thickens the contour to roughly `thickness` pixels wide. A full step grows
     * evenly in all eight directions (+2 px, so that curves do not get thicker than
     * straight lines), an odd remaining width is added on one side (+1 px). This yields
     * the configured width instead of double. Stays within the graphic.
     */
    internal fun thicken(src: BooleanArray, isGlyph: BooleanArray, width: Int, height: Int, thickness: Int): BooleanArray {
        val extra = thickness - 1
        if (extra <= 0) return src
        var cur = src
        repeat(extra / 2) { cur = grow(cur, isGlyph, width, height, symmetric = true) }
        if (extra % 2 == 1) cur = grow(cur, isGlyph, width, height, symmetric = false)
        return cur
    }

    private fun grow(src: BooleanArray, isGlyph: BooleanArray, width: Int, height: Int, symmetric: Boolean): BooleanArray {
        val next = BooleanArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (!isGlyph[i]) continue
                // One-sided step (left/top) for +1 px; full step additionally right/
                // bottom/diagonal for even +2 px.
                var on = src[i] || setAt(src, x - 1, y, width, height) || setAt(src, x, y - 1, width, height)
                if (symmetric) {
                    on = on || setAt(src, x + 1, y, width, height) || setAt(src, x, y + 1, width, height) ||
                        setAt(src, x - 1, y - 1, width, height) || setAt(src, x + 1, y - 1, width, height) ||
                        setAt(src, x - 1, y + 1, width, height) || setAt(src, x + 1, y + 1, width, height)
                }
                next[i] = on
            }
        }
        return next
    }

    private fun setAt(a: BooleanArray, x: Int, y: Int, width: Int, height: Int): Boolean =
        x in 0 until width && y in 0 until height && a[y * width + x]

    // Does the pixel border on background? The image edge counts as background only when
    // borderIsBackground is set (true for symbols, false for full-frame images).
    private fun touchesBackground(isGlyph: BooleanArray, x: Int, y: Int, width: Int, height: Int, borderIsBackground: Boolean): Boolean =
        isBackground(isGlyph, x - 1, y, width, height, borderIsBackground) ||
            isBackground(isGlyph, x + 1, y, width, height, borderIsBackground) ||
            isBackground(isGlyph, x, y - 1, width, height, borderIsBackground) ||
            isBackground(isGlyph, x, y + 1, width, height, borderIsBackground)

    private fun isBackground(isGlyph: BooleanArray, x: Int, y: Int, width: Int, height: Int, borderIsBackground: Boolean): Boolean =
        if (x in 0 until width && y in 0 until height) !isGlyph[y * width + x] else borderIsBackground

    /** "Inkiness": distance from white (0 = white, 255 = deep black/strong color). */
    private fun inkness(p: Int): Int = 255 - min((p shr 16) and 0xFF, min((p shr 8) and 0xFF, p and 0xFF))

    // Largest color jump to a LIGHTER glyph neighbor (this pixel is the inky side).
    private fun maxInkEdge(argb: IntArray, isGlyph: BooleanArray, x: Int, y: Int, width: Int, height: Int): Float {
        val p = argb[y * width + x]
        val inkP = inkness(p)
        var m = edgeTo(argb, isGlyph, x - 1, y, width, height, p, inkP)
        m = max(m, edgeTo(argb, isGlyph, x + 1, y, width, height, p, inkP))
        m = max(m, edgeTo(argb, isGlyph, x, y - 1, width, height, p, inkP))
        m = max(m, edgeTo(argb, isGlyph, x, y + 1, width, height, p, inkP))
        return m
    }

    private fun edgeTo(argb: IntArray, isGlyph: BooleanArray, x: Int, y: Int, width: Int, height: Int, p: Int, inkP: Int): Float {
        if (x !in 0 until width || y !in 0 until height) return 0f
        val j = y * width + x
        if (!isGlyph[j]) return 0f
        val q = argb[j]
        // Only mark the inkier side of the edge; with equal inkiness, deterministically
        // choose ONE side (the one with the larger RGB value), otherwise colored edges double.
        val inkQ = inkness(q)
        if (inkP < inkQ || (inkP == inkQ && (p and 0xFFFFFF) < (q and 0xFFFFFF))) return 0f
        val dr = abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF))
        val dg = abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF))
        val db = abs((p and 0xFF) - (q and 0xFF))
        return max(dr, max(dg, db)).toFloat()
    }
}
