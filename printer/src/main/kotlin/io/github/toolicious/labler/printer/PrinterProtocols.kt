package io.github.toolicious.labler.printer

/** Registry of the printer families the app supports. */
object PrinterProtocols {

    val ALL: List<PrinterProtocol> = listOf(PhomemoProtocol)

    val DEFAULT: PrinterProtocol = PhomemoProtocol

    fun of(family: PrinterFamily): PrinterProtocol =
        ALL.firstOrNull { it.family == family } ?: DEFAULT

    /** The family an advertised name belongs to, or null if it is not a printer we know. */
    fun matchName(name: String): PrinterProtocol? = ALL.firstOrNull { it.ble.matches(name) }

    /** Every name prefix that identifies a supported printer, for the scan filter. */
    val NAME_PREFIXES: List<String> = ALL.flatMap { it.ble.namePrefixes }
}
