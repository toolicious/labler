package io.github.toolicious.labler.ble

import io.github.toolicious.labler.printer.BleProfile
import java.util.UUID

/**
 * The UUIDs of one printer family, resolved from its [BleProfile]. The protocol module keeps
 * them as strings so it stays free of Android and JVM UUID types; this is where they turn into
 * the objects the GATT layer wants.
 */
class PrinterUuids private constructor(profile: BleProfile) {

    val service: UUID = UUID.fromString(profile.serviceUuid)
    val write: UUID = UUID.fromString(profile.writeCharUuid)

    /** Null for a family that never sends anything back. */
    val notify: UUID? = profile.notifyCharUuid?.let { UUID.fromString(it) }

    companion object {
        /** Client Characteristic Configuration Descriptor (for notify subscription). */
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val cache = HashMap<BleProfile, PrinterUuids>()

        /** Parsing UUIDs is not free, and a connection asks for them repeatedly. */
        @Synchronized
        fun of(profile: BleProfile): PrinterUuids = cache.getOrPut(profile) { PrinterUuids(profile) }
    }
}
