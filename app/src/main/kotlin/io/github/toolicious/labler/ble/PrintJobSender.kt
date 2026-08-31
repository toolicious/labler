package io.github.toolicious.labler.ble

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
        val transport = connection.protocol.transport
        // Framed up front, because a family that wraps its chunks makes them longer than the
        // job itself and the progress would otherwise never reach 1.
        val jobs = payloads.map { connection.protocol.framePayload(it, connection.chunkSize) }
        val totalBytes = jobs.sumOf { chunks -> chunks.sumOf { it.size } }.toLong()
        var sent = 0L
        jobs.forEachIndexed { index, chunks ->
            if (index > 0) delay(transport.copyDelayMs)
            for (chunk in chunks) {
                connection.client.writeCharacteristic(connection.writeChar, chunk)
                delay(transport.chunkDelayMs)
                sent += chunk.size
                onProgress(sent.toFloat() / totalBytes, index + 1)
            }
        }
    }
}
