package io.github.toolicious.labler.printer

/**
 * A value about a printer that cannot be settled from a protocol description, only by holding the
 * printout next to a ruler.
 *
 * A family declares the ones that apply to it in [PrinterProtocol.tunables], a development build
 * offers exactly those for editing, and a release build offers none. The point is that someone
 * testing a printer nobody here owns can work through the possibilities in one sitting instead of
 * waiting for a new APK per guess.
 */
enum class Tunable(val kind: Kind, val availability: Availability) {
    /**
     * Dots per millimeter along the tape, which is how far the printer really feeds per
     * printed line. Not the pitch of the print head: that one is a specified property of a
     * bought-in part, while the feed comes out of motor, gearing and platen roller and lands
     * wherever those put it. Off by a per cent or two, a printed scale is useless, so this is
     * the one value a user gets to correct for their own device.
     */
    DOTS_PER_MM(Kind.NUMBER, Availability.RELEASE),

    /** Dots the head really prints. Wrong value: a row is missing at the top or bottom. */
    HEAD_DOTS(Kind.NUMBER, Availability.DEVELOPMENT),

    /** Where row 0 sits inside the column word. Wrong value: everything is off by one row. */
    ROW_BIT_OFFSET(Kind.NUMBER, Availability.DEVELOPMENT),

    /** Byte order within a raster column. Wrong value: the print is mirrored top to bottom. */
    REVERSE_COLUMN_BYTES(Kind.FLAG, Availability.DEVELOPMENT),

    /** Whether to wait for the printer to report a finished job. Off makes a test run quicker. */
    AWAIT_PRINT_RESULT(Kind.FLAG, Availability.DEVELOPMENT),
    ;

    /** What the value looks like, so a settings screen can offer the right control for it. */
    enum class Kind { NUMBER, FLAG }

    /**
     * Who gets to change it. [RELEASE] is a value a user has a reason to correct on their own
     * device; [DEVELOPMENT] is a guess about a printer nobody here owns and has no business in
     * a shipped app.
     */
    enum class Availability { RELEASE, DEVELOPMENT }
}

/**
 * Overrides for the [Tunable] values of one family, as plain text so that any future value fits
 * without touching the storage. Empty means every value stays at what the protocol declares,
 * which is what a release build always uses.
 */
data class ProtocolTuning(val values: Map<Tunable, String> = emptyMap()) {

    fun float(tunable: Tunable): Float? = values[tunable]?.toFloatOrNull()

    fun int(tunable: Tunable): Int? = values[tunable]?.toIntOrNull()

    fun bool(tunable: Tunable): Boolean? = values[tunable]?.toBooleanStrictOrNull()

    val isEmpty: Boolean get() = values.isEmpty()

    /** Only the values a shipped app lets a user change. */
    fun releaseOnly(): ProtocolTuning =
        ProtocolTuning(values.filterKeys { it.availability == Tunable.Availability.RELEASE })

    companion object {
        val NONE = ProtocolTuning()
    }
}
