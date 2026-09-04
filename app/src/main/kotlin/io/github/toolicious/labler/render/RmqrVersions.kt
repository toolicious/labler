package io.github.toolicious.labler.render

/**
 * Version table of the rectangular Micro QR Code, ISO/IEC 23941:2022.
 *
 * The numbers are facts out of the standard, which is not free to read. They were therefore taken
 * from two implementations written independently of each other, Zint's `backend/qr.h` and the
 * Python library `OUDON/rmqrcode-python`, and compared value by value. All 32 versions agreed on
 * every field except two, where the Python tables contradict themselves and Zint is right:
 * R13x27 carries 12 data codewords rather than 14, and R17x43 puts 61 rather than 60 codewords in
 * its single block. This file follows Zint at both.
 *
 * Only error correction level M is listed. The standard also defines H; adding it later means one
 * more column here and nothing in the encoder.
 *
 * This file is generated from those tables rather than typed, so a transposed digit cannot creep
 * in. [RmqrEncoderTest] checks it back against the reference.
 */

/** One rMQR symbol size. [index] is the 5-bit version indicator the format information carries. */
internal data class RmqrVersion(
    val index: Int,
    val height: Int,
    val width: Int,
    /** Data plus error correction, the whole symbol. */
    val totalCodewords: Int,
    val dataCodewords: Int,
    /** Reed-Solomon blocks the codewords are spread over. */
    val blocks: Int,
    val cciNumeric: Int,
    val cciAlphanumeric: Int,
    val cciByte: Int,
) {
    val name: String get() = "R${height}x$width"
}

private fun V(
    index: Int, height: Int, width: Int, total: Int, data: Int, blocks: Int,
    cciNumeric: Int, cciAlphanumeric: Int, cciByte: Int,
) = RmqrVersion(index, height, width, total, data, blocks, cciNumeric, cciAlphanumeric, cciByte)

/** All 32 versions, in the order of their version indicator: R7x43 first, R17x139 last. */
internal val RMQR_VERSIONS: List<RmqrVersion> = listOf(
    V( 0,  7,  43,  13,   6, 1, 4, 3, 3),
    V( 1,  7,  59,  21,  12, 1, 5, 5, 4),
    V( 2,  7,  77,  32,  20, 1, 6, 5, 5),
    V( 3,  7,  99,  44,  28, 1, 7, 6, 5),
    V( 4,  7, 139,  68,  44, 1, 7, 6, 6),
    V( 5,  9,  43,  21,  12, 1, 5, 5, 4),
    V( 6,  9,  59,  33,  21, 1, 6, 5, 5),
    V( 7,  9,  77,  49,  31, 1, 7, 6, 5),
    V( 8,  9,  99,  66,  42, 1, 7, 6, 6),
    V( 9,  9, 139,  99,  63, 2, 8, 7, 6),
    V(10, 11,  27,  15,   7, 1, 4, 4, 3),
    V(11, 11,  43,  31,  19, 1, 6, 5, 5),
    V(12, 11,  59,  47,  31, 1, 7, 6, 5),
    V(13, 11,  77,  67,  43, 1, 7, 6, 6),
    V(14, 11,  99,  89,  57, 2, 8, 7, 6),
    V(15, 11, 139, 132,  84, 2, 8, 7, 7),
    V(16, 13,  27,  21,  12, 1, 5, 5, 4),
    V(17, 13,  43,  41,  27, 1, 6, 6, 5),
    V(18, 13,  59,  60,  38, 1, 7, 6, 6),
    V(19, 13,  77,  85,  53, 2, 7, 7, 6),
    V(20, 13,  99, 113,  73, 2, 8, 7, 7),
    V(21, 13, 139, 166, 106, 3, 8, 8, 7),
    V(22, 15,  43,  51,  33, 1, 7, 6, 6),
    V(23, 15,  59,  74,  48, 1, 7, 7, 6),
    V(24, 15,  77, 103,  67, 2, 8, 7, 7),
    V(25, 15,  99, 136,  88, 2, 8, 7, 7),
    V(26, 15, 139, 199, 127, 3, 9, 8, 7),
    V(27, 17,  43,  61,  39, 1, 7, 6, 6),
    V(28, 17,  59,  88,  56, 2, 8, 7, 6),
    V(29, 17,  77, 122,  78, 2, 8, 7, 7),
    V(30, 17,  99, 160, 100, 3, 8, 8, 7),
    V(31, 17, 139, 232, 152, 4, 9, 8, 8),
)

/**
 * Column centres of the alignment patterns, by symbol width. The narrowest symbol carries none.
 * Every alignment pattern spans the full height of the symbol.
 */
internal fun rmqrAlignmentColumns(width: Int): IntArray = when (width) {
    43 -> intArrayOf(21)
    59 -> intArrayOf(19, 39)
    77 -> intArrayOf(25, 51)
    99 -> intArrayOf(23, 49, 75)
    139 -> intArrayOf(27, 55, 83, 111)
    else -> IntArray(0)
}
