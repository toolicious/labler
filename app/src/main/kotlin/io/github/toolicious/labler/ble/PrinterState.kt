package io.github.toolicious.labler.ble

/** State of the printer connection, observable app-wide through the PrinterManager. */
sealed interface PrinterState {
    data object Disconnected : PrinterState
    data class Connecting(val attempt: Int) : PrinterState
    data class Ready(val name: String, val address: String, val batteryPercent: Int?) : PrinterState
    data class Printing(val progress: Float, val copy: Int, val copies: Int) : PrinterState
    data class Error(val message: String) : PrinterState
}
