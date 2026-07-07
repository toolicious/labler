package io.github.toolicious.labler.printer.dither

import kotlin.math.abs
import kotlin.math.pow

/**
 * Combined control for grayscale values before halftoning: a single `amount` in
 * [-100, 100] (0 = neutral) steers brightness and contrast together.
 *
 * It shifts the effective switching threshold T and lays a mild contrast F on top
 * of it. The decisive part is the CONCAVE curve of the threshold: it rises quickly
 * at the start and becomes fine towards the end. As a result, much of the slider
 * travel sits precisely in the light tonal range (near white), where light
 * graphics such as the cake emoji only begin to react. At maximum the threshold
 * reaches almost white (254), so that a shape can even be filled in completely;
 * pure white (255, including the background) stays untouched.
 *
 * out = clamp((g - T) · F + 128, 0..255)
 *   span = 126 · (1 − (1 − |c|)^2.5)     (c = amount/100)  → 0 … 126, concave
 *   T    = 128 + sign(c) · span                              → ~2 … 254
 *   F    = 1 + |c| · 0.6                                      → 1 … 1.6
 *
 * With threshold halftoning a pixel turns black when g < T. amount = 0 is the
 * identity. The ordering is preserved (monotonic), so gradient methods still
 * show structure.
 */
object Contrast {

    fun adjust(gray: FloatArray, amount: Int): FloatArray {
        if (amount == 0) return gray
        val c = amount.coerceIn(-100, 100) / 100f
        val m = abs(c)
        val span = 126f * (1f - (1f - m).pow(2.5f))
        val threshold = 128f + (if (c >= 0f) span else -span)
        val f = 1f + m * 0.6f
        return FloatArray(gray.size) { i ->
            ((gray[i] - threshold) * f + 128f).coerceIn(0f, 255f)
        }
    }
}
