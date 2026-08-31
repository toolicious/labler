package io.github.toolicious.labler.printer

/** Cleanup of advertised BLE names, applied before any family can be told apart. */
object DeviceNames {

    /**
     * An advertised name with the padding some printers send along stripped off: control
     * characters, and the replacement character Android puts in for bytes that are not valid
     * UTF-8. One of them turns "P12_xxxx_BLE" into "P12_xxxx_BLE?", which shows up as a box
     * and defeats every match on the "_BLE" suffix.
     */
    fun clean(raw: String): String =
        raw.filter { !it.isISOControl() && it != '\uFFFD' }.trim()
}
