package io.github.toolicious.labler.printer

/**
 * What a printer reports back once a job is through, for a family that says so at all.
 *
 * [printed] separates the two that put ink on the tape from the ones that did not, which is what
 * decides whether the app reports a failure. The rest is detail for the message.
 */
enum class PrintResult(val printed: Boolean) {
    OK(true),

    /** Printed, but the battery is nearly flat; the next job may not make it. */
    OK_LOW_BATTERY(true),

    /** Stopped at the device. */
    CANCELLED(false),

    /** Refused: not enough charge left to print. */
    LOW_BATTERY(false),

    /** Refused: no tape cassette in the printer. */
    NO_CASSETTE(false),

    /** Refused for a reason the printer does not spell out. */
    FAILED(false),
}
