package io.github.toolicious.labler.printer

/**
 * 1-bit image in printer geometry: width = label length in dots (feed direction),
 * height fixed at 96 dots across the print head. true = black.
 */
class MonoImage(val width: Int, val black: BooleanArray) {

    val height: Int get() = Protocol.HEAD_DOTS

    init {
        require(width in 1..0xFFFF) { "Label length must be 1..65535 dots, was $width" }
        require(black.size == width * Protocol.HEAD_DOTS) {
            "Pixel buffer does not fit: ${black.size} instead of ${width * Protocol.HEAD_DOTS}"
        }
    }

    fun isBlack(x: Int, y: Int): Boolean = black[y * width + x]

    fun setBlack(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) black[y * width + x] = true
    }

    companion object {
        fun blank(width: Int): MonoImage = MonoImage(width, BooleanArray(width * Protocol.HEAD_DOTS))
    }
}
