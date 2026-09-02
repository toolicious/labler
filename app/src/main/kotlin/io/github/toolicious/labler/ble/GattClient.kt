package io.github.toolicious.labler.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * A GATT operation did not produce its callback in time (printer switched off, out of
 * range, radio wedged).
 *
 * Deliberately NOT a CancellationException, unlike the TimeoutCancellationException that
 * withTimeout throws: a timed-out radio operation is an I/O failure, and callers must be
 * able to tell it apart from a coroutine that was genuinely canceled. Handlers of the
 * shape `catch (c: CancellationException) { throw c }` would otherwise swallow it and
 * leave the caller hanging with no error at all.
 *
 * Carries no message on purpose, so callers fall back to their own localized text; the
 * technical detail goes to the log instead of into the UI.
 */
class GattTimeoutException(cause: Throwable? = null) : IOException(null, cause)

/**
 * Thin coroutine wrapper around BluetoothGatt. Android allows exactly one
 * pending GATT operation at a time; all operations therefore run serially
 * under a single mutex and wait for their corresponding callback event.
 */
@SuppressLint("MissingPermission") // Permissions are ensured before every action in the UI
class GattClient {

    private val _events = MutableSharedFlow<GattEvent>(extraBufferCapacity = 64)
    private val opMutex = Mutex()
    private var gatt: BluetoothGatt? = null

    /** All GATT events as a SharedFlow (e.g. for query responses via notify). */
    val events: SharedFlow<GattEvent> = _events

    val notifications: Flow<GattEvent.Notification> = _events.filterIsInstance<GattEvent.Notification>()
    val connectionChanges: Flow<GattEvent.ConnectionChange> = _events.filterIsInstance<GattEvent.ConnectionChange>()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            _events.tryEmit(GattEvent.ConnectionChange(status, newState))
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            _events.tryEmit(GattEvent.ServicesDiscovered(status))
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            _events.tryEmit(GattEvent.MtuChanged(mtu, status))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            _events.tryEmit(GattEvent.CharWritten(ch.uuid, status))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            _events.tryEmit(GattEvent.DescriptorWritten(d.uuid, status))
        }

        @Deprecated("Only relevant up to API 32")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                _events.tryEmit(GattEvent.Notification(ch.uuid, ch.value ?: ByteArray(0)))
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            _events.tryEmit(GattEvent.Notification(ch.uuid, value))
        }
    }

    /**
     * Starts a GATT operation and waits for the matching event.
     * onSubscription guarantees that no event is lost between the start
     * and the beginning of observation.
     */
    private suspend inline fun <reified E : GattEvent> awaitEvent(
        timeoutMs: Long,
        crossinline filter: (E) -> Boolean = { true },
        crossinline initiate: () -> Boolean,
    ): E = opMutex.withLock {
        try {
            withTimeout(timeoutMs) {
                events
                    .onSubscription { check(initiate()) { "GATT operation could not be started" } }
                    .filterIsInstance<E>()
                    .first { filter(it) }
            }
        } catch (e: TimeoutCancellationException) {
            bleLog("GATT operation timed out after $timeoutMs ms waiting for ${E::class.simpleName}")
            throw GattTimeoutException(e)
        }
    }

    /**
     * Connects (from the main thread, TRANSPORT_LE) and waits for the established connection.
     * autoConnect=true uses Android's power-saving background reconnect logic: the
     * connection is established as soon as the printer reappears (then timeoutMs=null,
     * to wait indefinitely).
     */
    suspend fun connect(
        context: Context,
        device: BluetoothDevice,
        autoConnect: Boolean = false,
        timeoutMs: Long? = 10_000,
    ) {
        opMutex.withLock {
            val await: suspend () -> Unit = {
                events
                    .onSubscription {
                        bleLog("connectGatt(autoConnect=$autoConnect) for ${device.address}")
                        gatt = withContext(Dispatchers.Main) {
                            device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
                        } ?: error("connectGatt returned null")
                    }
                    .filterIsInstance<GattEvent.ConnectionChange>()
                    .first { ev ->
                        bleLog("conn event: status=${ev.status} newState=${ev.newState} autoConnect=$autoConnect")
                        when {
                            ev.status == BluetoothGatt.GATT_SUCCESS &&
                                ev.newState == BluetoothProfile.STATE_CONNECTED -> true
                            // Background reconnect: the system keeps retrying on its own, so ignore the
                            // transient failures/disconnects it reports and keep waiting for the printer
                            // to actually appear. Only an active connect turns a bad status into an error.
                            autoConnect -> false
                            ev.status != BluetoothGatt.GATT_SUCCESS ->
                                error("Connection failed (GATT status ${ev.status})")
                            else -> false
                        }
                    }
            }
            if (timeoutMs == null) {
                await()
            } else {
                try {
                    withTimeout(timeoutMs) { await() }
                } catch (e: TimeoutCancellationException) {
                    // As a CancellationException this would abort connectInternal's retry loop
                    // instead of counting as one failed attempt.
                    bleLog("connect timed out after $timeoutMs ms")
                    throw GattTimeoutException(e)
                }
            }
        }
    }

    suspend fun discoverServices(timeoutMs: Long = 8_000) {
        val g = requireGatt()
        val ev = awaitEvent<GattEvent.ServicesDiscovered>(timeoutMs) { g.discoverServices() }
        check(ev.status == BluetoothGatt.GATT_SUCCESS) { "Service discovery failed (status ${ev.status})" }
    }

    /** Negotiates the MTU; returns the actual MTU (or -1 on failure). */
    suspend fun requestMtu(mtu: Int, timeoutMs: Long = 5_000): Int {
        val g = requireGatt()
        return try {
            val ev = awaitEvent<GattEvent.MtuChanged>(timeoutMs) { g.requestMtu(mtu) }
            if (ev.status == BluetoothGatt.GATT_SUCCESS) ev.mtu else -1
        } catch (_: GattTimeoutException) {
            // A missing MTU negotiation is not fatal, the default MTU still works.
            -1
        }
    }

    fun findCharacteristic(service: UUID, characteristic: UUID): BluetoothGattCharacteristic? =
        gatt?.getService(service)?.getCharacteristic(characteristic)

    suspend fun writeCharacteristic(
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
        timeoutMs: Long = 4_000,
    ) {
        val g = requireGatt()
        val ev = awaitEvent<GattEvent.CharWritten>(timeoutMs, filter = { it.uuid == ch.uuid }) {
            writeCompat(g, ch, value)
        }
        check(ev.status == BluetoothGatt.GATT_SUCCESS) { "Write failed (status ${ev.status})" }
    }

    suspend fun enableNotifications(ch: BluetoothGattCharacteristic, timeoutMs: Long = 4_000) {
        val g = requireGatt()
        check(g.setCharacteristicNotification(ch, true)) { "Notification registration failed" }
        val cccd = ch.getDescriptor(PrinterUuids.CCCD) ?: error("CCCD descriptor missing")
        val ev = awaitEvent<GattEvent.DescriptorWritten>(timeoutMs, filter = { it.uuid == PrinterUuids.CCCD }) {
            writeDescriptorCompat(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
        check(ev.status == BluetoothGatt.GATT_SUCCESS) { "CCCD write failed (status ${ev.status})" }
    }

    fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    private fun requireGatt(): BluetoothGatt = gatt ?: error("Not connected")

    @Suppress("DEPRECATION")
    private fun writeCompat(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ch.value = value
            g.writeCharacteristic(ch)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = value
            g.writeDescriptor(d)
        }
}
