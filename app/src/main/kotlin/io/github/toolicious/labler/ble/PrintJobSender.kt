package io.github.toolicious.labler.ble

import io.github.toolicious.labler.printer.Chunker
import io.github.toolicious.labler.printer.Protocol
import kotlinx.coroutines.delay

object PrintJobSender {

    /**
     * Sends multiple finished print jobs (e.g. copies) sequentially in chunks.
     * onProgress receives the overall progress 0..1 and the current job number (1-based).
     */
    suspend fun sendAll(
        connection: PrinterConnection,
        payloads: List<ByteArray>,
        onProgress: (Float, Int) -> Unit = { _, _ -> },
    ) {
        require(payloads.isNotEmpty()) { "At least one print job" }
        val totalBytes = payloads.sumOf { it.size }.toLong()
        var sent = 0L
        payloads.forEachIndexed { index, payload ->
            if (index > 0) delay(Protocol.COPY_DELAY_MS)
            for (chunk in Chunker.chunk(payload, connection.chunkSize)) {
                connection.client.writeCharacteristic(connection.writeChar, chunk)
                delay(Protocol.CHUNK_DELAY_MS)
                sent += chunk.size
                onProgress(sent.toFloat() / totalBytes, index + 1)
            }
        }
    }
}
