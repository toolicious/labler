package io.github.toolicious.labler.printer.dither

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DitherTest {

    private fun gradient(width: Int, height: Int): FloatArray =
        FloatArray(width * height) { i -> (i % width) * 255f / (width - 1) }

    @Test
    fun `Threshold splits exactly at 128`() {
        val gray = floatArrayOf(0f, 127.9f, 128f, 255f)
        val black = ThresholdDitherer.dither(gray, 4, 1)
        assertEquals(listOf(true, true, false, false), black.toList())
    }

    @Test
    fun `Error diffusion approximately preserves the mean gray value`() {
        val w = 64
        val h = 64
        for (ditherer in listOf(FloydSteinbergDitherer, AtkinsonDitherer)) {
            val gray = FloatArray(w * h) { 128f }
            val black = ditherer.dither(gray, w, h)
            val ratio = black.count { it }.toFloat() / black.size
            assertTrue(ratio in 0.3f..0.7f, "${ditherer::class.simpleName}: black ratio $ratio")
        }
    }

    @Test
    fun `Dithering is deterministic`() {
        val gray = gradient(32, 32)
        for (mode in DitherMode.entries) {
            val d = Ditherer.of(mode)
            assertEquals(
                d.dither(gray, 32, 32).toList(),
                d.dither(gray, 32, 32).toList(),
                "Mode $mode"
            )
        }
    }

    @Test
    fun `White stays white and black stays black`() {
        for (mode in DitherMode.entries) {
            val d = Ditherer.of(mode)
            assertTrue(d.dither(FloatArray(64) { 255f }, 8, 8).none { it }, "$mode: white")
            assertTrue(d.dither(FloatArray(64) { 0f }, 8, 8).all { it }, "$mode: black")
        }
    }
}
