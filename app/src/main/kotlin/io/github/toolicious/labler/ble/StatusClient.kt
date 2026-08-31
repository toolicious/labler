package io.github.toolicious.labler.ble

import io.github.toolicious.labler.printer.PrintResult
import io.github.toolicious.labler.printer.PrinterProtocol
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeout

data class PrinterInfo(
    val model: String?,
    val firmware: String?,
    val serial: String?,
    val hardware: String?,
)

/**
 * Everything the printer says about itself, over the notify characteristic of its print service.
 *
 * Two families use that channel differently. One answers questions put to it (battery, model,
 * firmware, serial, hardware), the other stays quiet until a job is through and then reports how
 * it went. Both are strictly optional: with the characteristic missing or the printer silent,
 * every method here returns null and printing is unaffected.
 */
class StatusClient(
    private val client: GattClient,
    private val uuids: PrinterUuids,
    private val protocol: PrinterProtocol,
) {

    private val queries = protocol.statusQueries
    private var ready = false

    /** Enables notifications. false if the characteristic is missing. */
    suspend fun initialize(): Boolean {
        val notifyUuid = uuids.notify ?: return false
        val notify = client.findCharacteristic(uuids.service, notifyUuid) ?: return false
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
        val q = queries ?: return null
        var last: Int? = null
        repeat(4) { attempt ->
            val pct = query(q.battery)?.let { resp ->
                if (resp.size >= 2) (resp[1].toInt() and 0xFF).coerceIn(0, 100) else null
            }
            if (pct != null) last = pct
            if (pct != null && pct > 0) return pct
            if (attempt < 3) delay(120)
        }
        return last
    }

    suspend fun printerInfo(): PrinterInfo {
        val q = queries ?: return PrinterInfo(null, null, null, null)
        return PrinterInfo(
            model = queryText(q.model),
            firmware = queryText(q.firmware),
            serial = queryText(q.serial),
            hardware = queryText(q.hardware),
        )
    }

    /**
     * Runs [send] and waits for the printer's verdict on the job it just received.
     *
     * The write happens from inside the collector, so a printer that answers the instant the last
     * byte lands cannot beat the subscription to it. The first reply only says printing has begun,
     * the one after it carries the result. Null on a timeout, which is deliberately not an error:
     * the tape has most likely come out fine and a job is not worth failing over a missing receipt.
     */
    suspend fun awaitPrintResult(timeoutMs: Long, send: suspend () -> Unit): PrintResult? {
        val notifyUuid = uuids.notify
        if (!ready || notifyUuid == null) {
            send()
            return null
        }
        return try {
            withTimeout(timeoutMs) {
                client.events
                    .onSubscription { send() }
                    .filterIsInstance<GattEvent.Notification>()
                    .filter { it.uuid == notifyUuid }
                    .mapNotNull { protocol.parsePrintResult(it.value) }
                    .drop(1)
                    .first()
            }
        } catch (timeout: TimeoutCancellationException) {
            // Only the wait ran out. A failed write throws out of here instead.
            null
        }
    }

    private suspend fun queryText(cmd: ByteArray): String? =
        query(cmd)
            ?.toString(Charsets.UTF_8)
            ?.filter { it.code in 32..126 }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private suspend fun query(cmd: ByteArray, timeoutMs: Long = 1000): ByteArray? {
        if (!ready || queries == null) return null
        val notifyUuid = uuids.notify ?: return null
        val writeChar = client.findCharacteristic(uuids.service, uuids.write) ?: return null
        val response = runCatching {
            withTimeout(timeoutMs) {
                client.events
                    .onSubscription { client.writeCharacteristic(writeChar, cmd) }
                    .filterIsInstance<GattEvent.Notification>()
                    .first { it.uuid == notifyUuid }
            }
        }.getOrNull()
        delay(protocol.transport.queryGapMs)
        return response?.value
    }
}
