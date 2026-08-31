package io.github.toolicious.labler.printer

/**
 * 1-bit image in printer geometry: width = label length in dots (feed direction),
 * height = the dots across the print head of the family it was rendered for. true = black.
 */
class MonoImage(val width: Int, val height: Int, val black: BooleanArray) {

    /** Bytes one raster column takes, the print head rounded up to whole bytes. */
    val bytesPerColumn: Int get() = (height + 7) / 8

    init {
        require(width in 1..0xFFFF) { "Label length must be 1..65535 dots, was $width" }
        require(height > 0) { "Print head must be at least 1 dot, was $height" }
        require(black.size == width * height) {
            "Pixel buffer does not fit: ${black.size} instead of ${width * height}"
        }
    }

    fun isBlack(x: Int, y: Int): Boolean = black[y * width + x]

    fun setBlack(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) black[y * width + x] = true
    }

    companion object {
        fun blank(width: Int, height: Int): MonoImage =
            MonoImage(width, height, BooleanArray(width * height))
    }
}
