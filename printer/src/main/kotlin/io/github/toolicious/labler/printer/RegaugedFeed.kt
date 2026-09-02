package io.github.toolicious.labler.printer

/**
 * A protocol with a corrected feed resolution, everything else delegated untouched.
 *
 * Only sound for a family whose command bytes do not themselves depend on [geometry]: the
 * delegated methods run on [base] and would read its original value. That holds for the Phomemo
 * family, where the raster header counts dots and never millimetres. A family that needs more
 * than this implements [PrinterProtocol.withTuning] itself, the way the Dymo does.
 */
internal class RegaugedFeed(
    private val base: PrinterProtocol,
    override val geometry: HeadGeometry,
) : PrinterProtocol by base {

    override fun tunableValue(tunable: Tunable): String? =
        if (tunable == Tunable.DOTS_PER_MM) geometry.dotsPerMm.toString() else base.tunableValue(tunable)

    override fun withTuning(tuning: ProtocolTuning): PrinterProtocol = base.withTuning(tuning)
}
