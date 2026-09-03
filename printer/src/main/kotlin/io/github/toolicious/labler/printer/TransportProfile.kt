package io.github.toolicious.labler.printer

/** How much goes into one BLE write and how fast writes may follow each other. */
data class TransportProfile(
    val requestedMtu: Int,
    /** Bytes in one write when the negotiated packet has room for that many. */
    val chunkSize: Int,
    /**
     * Bytes in one write below which the family will not work at all.
     *
     * What a phone negotiates is not what was asked for, so the size actually used lands
     * somewhere between the two: as much of the packet as fits, capped at [chunkSize]. Under
     * this floor the connection fails instead, with the number a user can act on.
     */
    val minChunkSize: Int,
    val chunkDelayMs: Long,
    /** Pause between two copies of the same label. */
    val copyDelayMs: Long,
    /** Pause between two status queries. */
    val queryGapMs: Long,
) {
    companion object {
        /** Bytes of every BLE packet that ATT keeps for itself. */
        const val ATT_OVERHEAD = 3
    }
}
