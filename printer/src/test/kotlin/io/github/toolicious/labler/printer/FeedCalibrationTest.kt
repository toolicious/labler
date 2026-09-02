package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The arithmetic behind the calibration dialog: the pattern puts a known number of dots between
 * its first and last tick, the user supplies what the ruler showed, and the feed resolution comes
 * out of the two. Worked through with the figures actually measured on a P15.
 */
class FeedCalibrationTest {

    private val declared = PhomemoProtocol.geometry

    /** 240 dots came out 30.61 mm long on the device, where 30.00 was intended. */
    private val measuredMm = 30.61f

    private fun calibrated(measured: Float): PrinterProtocol {
        val span = TestPattern.tickSpanDots(declared)!!
        return PhomemoProtocol.withTuning(
            ProtocolTuning(mapOf(Tunable.DOTS_PER_MM to (span / measured).toString()))
        )
    }

    @Test
    fun `the pattern offers a span the user can measure`() {
        assertEquals(240, TestPattern.tickSpanDots(declared))
        // Which is what the app tells them to expect, at the resolution it currently assumes.
        assertEquals(30.0f, 240 / declared.dotsPerMm, 0.001f)
    }

    @Test
    fun `a label asks for the dots that really cover its length once calibrated`() {
        val truth = 240 / measuredMm
        assertEquals(40.0f, calibrated(measuredMm).geometry.mmToDots(40) / truth, 0.1f)
        // Without the correction the same label runs almost a millimeter long.
        assertEquals(40.8f, declared.mmToDots(40) / truth, 0.1f)
    }

    @Test
    fun `every tick of the next printout lands on the millimeter it is named after`() {
        val corrected = calibrated(measuredMm).geometry
        val truth = 240 / measuredMm
        val ticks = TestPattern.tickColumns(corrected)
        // A coarser grid fits one more tick into the same 320 dots, which is the point: the
        // pattern follows the millimeters, not a fixed dot spacing.
        assertEquals(8, ticks.size)
        ticks.forEach { (mm, x) ->
            // Where the tick really ends up on the tape, not where the assumed grid puts it.
            assertEquals(mm.toFloat(), x / truth, 0.1f, "tick labeled $mm mm")
        }
    }

    @Test
    fun `a nonsensical measurement is ignored rather than wrecking the geometry`() {
        assertSame(PhomemoProtocol, PhomemoProtocol.withTuning(ProtocolTuning.NONE))
        val zero = ProtocolTuning(mapOf(Tunable.DOTS_PER_MM to "0"))
        assertSame(PhomemoProtocol, PhomemoProtocol.withTuning(zero))
        val text = ProtocolTuning(mapOf(Tunable.DOTS_PER_MM to "kaputt"))
        assertSame(PhomemoProtocol, PhomemoProtocol.withTuning(text))
    }

    @Test
    fun `a release build keeps the feed correction and drops the rest`() {
        val mixed = ProtocolTuning(
            mapOf(
                Tunable.DOTS_PER_MM to "7.84",
                Tunable.ROW_BIT_OFFSET to "0",
                Tunable.AWAIT_PRINT_RESULT to "false",
            )
        )
        assertEquals(setOf(Tunable.DOTS_PER_MM), mixed.releaseOnly().values.keys)
    }

    @Test
    fun `correcting the feed leaves the printed bytes alone`() {
        val corrected = calibrated(measuredMm)
        val image = MonoImage.blank(8, PhomemoProtocol.HEAD_DOTS)
        assertEquals(
            PhomemoProtocol.buildJob(image, MediaType.DIE_CUT).toList(),
            corrected.buildJob(image, MediaType.DIE_CUT).toList(),
        )
    }
}
