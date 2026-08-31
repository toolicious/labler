package io.github.toolicious.labler.printer

/**
 * One printer family's wire protocol. Everything that differs between families sits behind this
 * interface, so the BLE layer, the renderer and the editor stay family-agnostic.
 */
interface PrinterProtocol {

    val family: PrinterFamily

    val geometry: HeadGeometry

    val ble: BleProfile

    val transport: TransportProfile

    /** Paper handling this family understands. A label may only use one of these. */
    val supportedMedia: Set<MediaType>

    /** Status commands, or null if the printer cannot be queried. */
    val statusQueries: StatusQueries?

    /** Whether the printer reports how a job went once it is through. */
    val awaitsPrintResult: Boolean get() = false

    /** Packs a label into the family's raster format. */
    fun packColumns(image: MonoImage): ByteArray

    /** Builds one complete print job (a single label). */
    fun buildJob(image: MonoImage, media: MediaType, density: Int? = null): ByteArray

    /**
     * Splits a finished job into the individual BLE writes. Most families just cut the byte
     * stream into [chunkSize] pieces; one that frames its chunks does it here.
     */
    fun framePayload(job: ByteArray, chunkSize: Int): List<ByteArray> =
        Chunker.chunk(job, chunkSize)

    /**
     * Reads a message the printer pushed on its notify characteristic as a print result,
     * or null if it is something else.
     */
    fun parsePrintResult(bytes: ByteArray): PrintResult? = null
}
