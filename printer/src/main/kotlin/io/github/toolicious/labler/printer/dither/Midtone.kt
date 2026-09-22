package io.github.toolicious.labler.printer.dither

import kotlin.math.ln
import kotlin.math.pow

/**
 * Move the highlights and shadows by adjusting the midpoint.
 *
 * If the midpoint parameter is less than 50, the filter brightens the midtones. A value greater
 * than 50 will darken the midtones. The midpoint must be within 0 to 100.
 *
 * For thermal printers, this filter is useful to compensate for the fact that a 50% dot coverage
 * isn't 50% gray because of the non-linear effects of thermal exposure (duration and power).
 *
 * This is a common image processing filter. You can do it in Gimp for instance, via the Levels
 * dialog box. (Use the gray slider.)
 *
 * It applies a non-linear power curve (gamma) filter so that the midpoint is shifted to the new
 * position.
 */
object Midtone {
    fun adjust(gray: FloatArray, midpoint: Int): FloatArray {
        if (midpoint == 50) return gray

        val m = midpoint.coerceIn(0, 100)

        /*
        The gamma function is:
        out = in.pow(g)
        where g is the gamma value and in and out is in the [0, 1] range.

        To determine the gamma value so that the new out data is on a new midpoint (given),
        we want to solve, for g:
        (midpoint / 100) = (50 / 100).pow(g)
        We start by applying ln on both sides of the equation:
        ln(midpoint / 100) = ln((50 / 100).pow(g))
        Where ln is the natural logarithm.
        And then use the logarithm power rule: ln(a.pow(b)) = b * ln(a) on the rhs:
        ln(midpoint / 100) = g * ln(50 / 100)
        which gives:
        g = ln(50 / 100) / (midpoint / 100)
        Then it is just a matter of applying the gamma function on the image.
        */
        val g = ln(0.5f) / ln((m / 100f).coerceIn(0.001f, 0.999f))
        return FloatArray(gray.size) { i ->
            ((gray[i] / 255f).pow(g) * 255f).coerceIn(0f, 255f)
        }
    }
}
