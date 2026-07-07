package io.github.toolicious.labler.printer

/**
 * Packs a MonoImage into the printer's column format: exactly 12 bytes per
 * image column x, mirrored vertically (invertedY), LSB = topmost row of the
 * respective group of 8. Together this produces the 90-degree rotation with
 * which the label designed sideways runs lengthwise under the 96-dot head.
 */
object ColumnPacker {

    fun packColumns(image: MonoImage): ByteArray {
        val out = ByteArray(image.width * Protocol.BYTES_PER_COLUMN)
        var i = 0
        for (x in 0 until image.width) {
            var y = 0
            while (y < Protocol.HEAD_DOTS) {
                val invertedY = Protocol.HEAD_DOTS - 8 - y
                var b = 0
                for (bit in 0 until 8) {
                    if (image.isBlack(x, invertedY + bit)) b = b or (1 shl bit)
                }
                out[i++] = b.toByte()
                y += 8
            }
        }
        return out
    }
}
