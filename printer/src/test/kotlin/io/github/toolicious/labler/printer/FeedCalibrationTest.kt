package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The arithmetic behind the calibration dialog: the pattern puts a known number of dots between
 * its two calibration marks, the user supplies what the ruler showed, and the feed resolution
 * comes out of the two. Worked through with the figures actually measured on a P15.
 */
class FeedCalibrationTest {

    private val declared = PhomemoProtocol.geometry

    /**
     * What the 236 dots between the marks come out as on the device, whose feed measured
     * 199.15 dpi against the 200 the app now assumes.
     */
    private val measuredMm = 30.10f

    private fun calibrated(measured: Float): PrinterProtocol {
        val span = TestPattern.calibrationSpanDots(declared)
        return PhomemoProtocol.withTuning(
            ProtocolTuning(mapOf(Tunable.DOTS_PER_MM to (span / measured).toString()))
        )
    }

    @Test
    fun `the pattern offers a span the user can measure`() {
        assertEquals(236, TestPattern.calibrationSpanDots(declared))
        // And it stands for the round number the dialog quotes, to within the odd dot.
        assertEquals(TestPattern.CALIBRATION_MM.toFloat(), 236 / declared.dotsPerMm, 0.05f)
    }

    @Test
    fun `a label asks for the dots that really cover its length once calibrated`() {
        val truth = 236 / measuredMm
        assertEquals(40.0f, calibrated(measuredMm).geometry.mmToDots(40) / truth, 0.1f)
        // Without the correction it runs two tenths long, where the 203 dpi the head is sold
        // with used to miss by almost a millimeter.
        assertEquals(40.2f, declared.mmToDots(40) / truth, 0.1f)
    }

    @Test
    fun `every tick of the next printout lands on the millimeter it is named after`() {
        val corrected = calibrated(measuredMm).geometry
        val truth = 236 / measuredMm
        val ticks = TestPattern.tickColumns(corrected)
        // The ticks follow the millimeters rather than a fixed dot spacing, so a correction
        // moves every one of them.
        assertEquals(7, ticks.size)
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
