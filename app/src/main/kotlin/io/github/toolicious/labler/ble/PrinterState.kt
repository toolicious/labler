package io.github.toolicious.labler.ble

import io.github.toolicious.labler.printer.PrinterFamily

/** State of the printer connection, observable app-wide through the PrinterManager. */
sealed interface PrinterState {
    data object Disconnected : PrinterState
    data class Connecting(val attempt: Int) : PrinterState
    data class Ready(
        val name: String,
        val address: String,
        val batteryPercent: Int?,
        /** Which family is on the other end, so the UI can tell it apart from a label's own. */
        val family: PrinterFamily,
    ) : PrinterState
    data class Printing(val progress: Float, val copy: Int, val copies: Int) : PrinterState
    data class Error(val message: String) : PrinterState
}
