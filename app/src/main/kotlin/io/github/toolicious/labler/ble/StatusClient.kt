package io.github.toolicious.labler.ble

import io.github.toolicious.labler.printer.Protocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeout

data class PrinterInfo(
    val model: String?,
    val firmware: String?,
    val serial: String?,
    val hardware: String?,
)

/**
 * Status queries (battery, model, firmware, serial number, hardware). The commands go
 * to the print characteristic FF02, the responses arrive as notifications on FF01 of the
 * same service. Strictly optional: if FF01 is missing or the printer does not respond, all
 * methods return null; printing remains unaffected.
 */
class StatusClient(private val client: GattClient) {

    private var ready = false

    /** Enables notifications on FF01. false if the characteristic is missing. */
    suspend fun initialize(): Boolean {
        val notify = client.findCharacteristic(PrinterUuids.PRINT_SERVICE, PrinterUuids.PRINT_NOTIFY)
            ?: return false
        ready = runCatching { client.enableNotifications(notify) }.isSuccess
        return ready
    }

    /**
     * Discards the initial push that the printer sends once after notifications are enabled;
     * otherwise the first query captures it instead of the real response
     * (led, for example, to battery 0 %).
     */
    suspend fun drainInitialPush() {
        if (!ready) return
        // Discard the initial push: listen until ~200 ms of silence (max. 1.2 s), so that even
        // a push arriving somewhat later does not corrupt the first real query.
        runCatching {
            withTimeout(1200) {
                while (true) withTimeout(200) { client.notifications.first() }
            }
        }
    }

    /**
     * Battery in %. On the first query after a (re-)connect the printer occasionally returns
     * 0 (initial/spurious push); therefore, on 0/null query again briefly and take the first
     * plausible (>0) value. If it stays 0, 0 is accepted (genuinely empty).
     */
    suspend fun batteryPercent(): Int? {
        var last: Int? = null
        repeat(4) { attempt ->
            val pct = query(Protocol.QUERY_BATTERY)?.let { resp ->
                if (resp.size >= 2) (resp[1].toInt() and 0xFF).coerceIn(0, 100) else null
            }
            if (pct != null) last = pct
            if (pct != null && pct > 0) return pct
            if (attempt < 3) delay(120)
        }
        return last
    }

    suspend fun printerInfo(): PrinterInfo = PrinterInfo(
        model = queryText(Protocol.QUERY_MODEL),
        firmware = queryText(Protocol.QUERY_FIRMWARE),
        serial = queryText(Protocol.QUERY_SERIAL),
        hardware = queryText(Protocol.QUERY_HARDWARE),
    )

    private suspend fun queryText(cmd: ByteArray): String? =
        query(cmd)
            ?.toString(Charsets.UTF_8)
            ?.filter { it.code in 32..126 }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private suspend fun query(cmd: ByteArray, timeoutMs: Long = 1000): ByteArray? {
        if (!ready) return null
        val writeChar = client.findCharacteristic(PrinterUuids.PRINT_SERVICE, PrinterUuids.PRINT_WRITE)
            ?: return null
        val response = runCatching {
            withTimeout(timeoutMs) {
                client.events
                    .onSubscription { client.writeCharacteristic(writeChar, cmd) }
                    .filterIsInstance<GattEvent.Notification>()
                    .first { it.uuid == PrinterUuids.PRINT_NOTIFY }
            }
        }.getOrNull()
        delay(Protocol.QUERY_GAP_MS)
        return response?.value
    }
}
