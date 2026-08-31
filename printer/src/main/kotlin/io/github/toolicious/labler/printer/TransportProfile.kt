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
    /**
     * Whether [chunkSize] is a protocol requirement rather than a transfer optimisation.
     * A family that frames its chunks cannot simply send smaller ones, so a connection whose
     * MTU stays below [minMtuForFullChunks] has to fail instead of writing a broken job.
     */
    val requiresFullChunks: Boolean = false,
)
