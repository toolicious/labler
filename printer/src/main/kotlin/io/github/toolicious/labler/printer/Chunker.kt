package io.github.toolicious.labler.printer

/** Splits a job into BLE write chunks. */
object Chunker {

    fun chunk(payload: ByteArray, chunkSize: Int): List<ByteArray> {
        require(chunkSize > 0) { "chunkSize must be positive" }
        if (payload.isEmpty()) return emptyList()
        val chunks = ArrayList<ByteArray>((payload.size + chunkSize - 1) / chunkSize)
        var pos = 0
        while (pos < payload.size) {
            val end = minOf(pos + chunkSize, payload.size)
            chunks.add(payload.copyOfRange(pos, end))
            pos = end
        }
        return chunks
    }
}
