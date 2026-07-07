package io.github.toolicious.labler.ble

import java.util.UUID

/** GATT callback events as data types, so operations can wait for them. */
sealed interface GattEvent {
    data class ConnectionChange(val status: Int, val newState: Int) : GattEvent
    data class ServicesDiscovered(val status: Int) : GattEvent
    data class MtuChanged(val mtu: Int, val status: Int) : GattEvent
    data class CharWritten(val uuid: UUID, val status: Int) : GattEvent
    data class DescriptorWritten(val uuid: UUID, val status: Int) : GattEvent
    class Notification(val uuid: UUID, val value: ByteArray) : GattEvent
}
