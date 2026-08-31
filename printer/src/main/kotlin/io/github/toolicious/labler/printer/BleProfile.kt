package io.github.toolicious.labler.printer

/**
 * How a family is found and addressed over BLE. UUIDs stay strings so this module keeps to plain
 * JVM types; the app turns them into java.util.UUID.
 */
data class BleProfile(
    val serviceUuid: String,
    val writeCharUuid: String,
    /** Characteristic the printer answers on, or null for a family that never talks back. */
    val notifyCharUuid: String?,
    /** Advertised name prefixes that identify this family in a scan. */
    val namePrefixes: List<String>,
    /** Suffix of the advertised name that is noise to the user, e.g. "_BLE". */
    val nameSuffix: String? = null,
) {
    /** Whether an advertised name (already run through [DeviceNames.clean]) is this family. */
    fun matches(name: String): Boolean = namePrefixes.any { name.startsWith(it) }

    /** The advertised name as it should be shown to the user. */
    fun displayName(name: String): String =
        nameSuffix?.let { name.removeSuffix(it) } ?: name
}
