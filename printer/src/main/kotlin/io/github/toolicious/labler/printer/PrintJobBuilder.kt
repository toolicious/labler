package io.github.toolicious.labler.printer

import java.io.ByteArrayOutputStream

/** Paper type: die-cut labels with a gap or continuous tape. */
enum class MediaType { DIE_CUT, CONTINUOUS }

/**
 * Builds a complete print job (a single label) from a MonoImage, which is
 * sent unchanged in chunks over the write characteristic.
 */
object PrintJobBuilder {

    fun buildJob(image: MonoImage, media: MediaType): ByteArray {
        val payload = ColumnPacker.packColumns(image)
        val out = ByteArrayOutputStream(payload.size + 48)

        out.write(Protocol.INIT)
        repeat(Protocol.HEADER_PADDING) { out.write(0) }
        out.write(Protocol.PRINT_START)
        out.write(Protocol.RASTER_GS_V0)
        out.write(Protocol.BYTES_PER_COLUMN)          // xL: 12 bytes per print line
        out.write(0)                                  // xH
        out.write(image.width and 0xFF)               // yL: line count = label length
        out.write((image.width shr 8) and 0xFF)       // yH (little-endian)
        out.write(payload)

        when (media) {
            MediaType.DIE_CUT -> {
                out.write(Protocol.FORM_FEED)         // advance to the die-cut gap
                out.write(Protocol.PRINT_END)
                out.write(Protocol.INIT)
                out.write(Protocol.INIT)
            }
            MediaType.CONTINUOUS -> {
                out.write(Protocol.feedDots(Protocol.CONTINUOUS_FEED_DOTS))
                out.write(Protocol.PRINT_END)
            }
        }
        return out.toByteArray()
    }
}
