package io.github.toolicious.labler.printer

/** Registry of the printer families the app supports. */
object PrinterProtocols {

    private val BASE: List<PrinterProtocol> = listOf(PhomemoProtocol, DymoProtocol.DEFAULT)

    @Volatile
    private var resolved: List<PrinterProtocol> = BASE

    val ALL: List<PrinterProtocol> get() = resolved

    val DEFAULT: PrinterProtocol = PhomemoProtocol

    fun of(family: PrinterFamily): PrinterProtocol =
        resolved.firstOrNull { it.family == family } ?: DEFAULT

    /** The protocol as declared, without any calibration overrides on it. */
    fun baseOf(family: PrinterFamily): PrinterProtocol =
        BASE.firstOrNull { it.family == family } ?: DEFAULT

    /** The family an advertised name belongs to, or null if it is not a printer we know. */
    fun matchName(name: String): PrinterProtocol? = resolved.firstOrNull { it.ble.matches(name) }

    /** Every name prefix that identifies a supported printer, for the scan filter. */
    val NAME_PREFIXES: List<String> get() = resolved.flatMap { it.ble.namePrefixes }

    /**
     * Applies calibration overrides to the families that have any.
     *
     * Only a development build ever calls this with something in it; a release build leaves every
     * protocol exactly as it is declared. Resolved once per change rather than per lookup, because
     * [of] sits in the rendering path.
     */
    @Synchronized
    fun applyTuning(tuning: Map<PrinterFamily, ProtocolTuning>) {
        resolved = BASE.map { protocol ->
            protocol.withTuning(tuning[protocol.family] ?: ProtocolTuning.NONE)
        }
    }
}
