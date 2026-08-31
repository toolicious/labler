package io.github.toolicious.labler.printer

/** How much goes into one BLE write and how fast writes may follow each other. */
data class TransportProfile(
    val requestedMtu: Int,
    val chunkSize: Int,
    /** Chunk size used when the negotiated MTU is below [minMtuForFullChunks]. */
    val fallbackChunkSize: Int,
    val minMtuForFullChunks: Int,
    val chunkDelayMs: Long,
    /** Pause between two copies of the same label. */
    val copyDelayMs: Long,
    /** Pause between two status queries. */
    val queryGapMs: Long,
)
