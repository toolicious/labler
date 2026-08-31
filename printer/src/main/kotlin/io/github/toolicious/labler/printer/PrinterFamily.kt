package io.github.toolicious.labler.printer

/**
 * A family of printers that share one wire protocol and one print head geometry.
 *
 * Persisted by name in templates, print history, backups and settings, so the constants keep
 * their spelling. A name that cannot be resolved reads back as [PHOMEMO], which is what every
 * label created before the app knew about families is.
 */
enum class PrinterFamily {
    /** Phomemo/Marklife P15, P12, L13: 96-dot head, "0x10" protocol around an ESC/POS GS v 0 raster. */
    PHOMEMO,

    /** Dymo LetraTag 200B: 30-dot head on 12 mm tape, escape directives in a checksummed frame. */
    DYMO,
    ;

    companion object {
        val DEFAULT = PHOMEMO

        /** [valueOf] that falls back instead of throwing, for values read from storage. */
        fun ofName(name: String?): PrinterFamily =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
