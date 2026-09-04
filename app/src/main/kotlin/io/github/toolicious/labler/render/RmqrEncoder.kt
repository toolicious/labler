package io.github.toolicious.labler.render

import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder

/**
 * Encoder for the rectangular Micro QR Code, ISO/IEC 23941:2022.
 *
 * A QR code can only grow as tall as the tape is wide, so on a 12 mm label its modules quickly get
 * smaller than the print head can place cleanly. rMQR is the same idea laid out flat: it grows
 * along the tape, where there is room, and keeps its modules large (issue #26).
 *
 * Written from the standard's structure rather than ported. The numbers live in [RMQR_VERSIONS] and
 * are cross-checked between two independent implementations; the geometry below follows the
 * standard's own layout rules. Two things make rMQR simpler than QR: there is exactly one data
 * mask, so no mask is searched for and nothing is scored, and the error correction runs in the same
 * Galois field as QR, so ZXing's [ReedSolomonEncoder] does that part.
 *
 * Deliberately left out, none of which costs correctness, only capacity:
 * - Error correction level H. Only M is tabulated.
 * - The kanji mode, and splitting one text into several segments of different modes.
 * - An ECI marker for the byte mode. The bytes are UTF-8, which is what most readers assume; pure
 *   ASCII is unambiguous either way.
 */
internal object RmqrEncoder {

    /** Blank modules the standard asks for around a symbol. QR wants four, rMQR two. */
    const val QUIET_ZONE = 2

    /** A finished symbol, `modules[y][x]`, true where a module is dark. Without the quiet zone. */
    class Symbol(val version: RmqrVersion, val modules: Array<BooleanArray>) {
        val width: Int get() = version.width
        val height: Int get() = version.height
    }

    private const val ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    /** Mode indicators from the standard's Table 2, three bits each. */
    private enum class Mode(val indicator: Int) { NUMERIC(0b001), ALPHANUMERIC(0b010), BYTE(0b011) }

    /**
     * The symbol that fits [boxWidth] x [boxHeight] pixels best, or null when nothing does.
     *
     * "Best" is the largest whole-number module size, because that is the whole point of using a
     * rectangular code on a narrow tape. Where several versions tie, the one that fills the drawn
     * box furthest wins, so the code looks placed rather than lost in the middle.
     */
    fun fit(text: String, boxWidth: Int, boxHeight: Int): Symbol? {
        var best: Symbol? = null
        var bestScale = 0
        var bestArea = 0
        for (version in RMQR_VERSIONS) {
            val scale = minOf(
                boxWidth / (version.width + 2 * QUIET_ZONE),
                boxHeight / (version.height + 2 * QUIET_ZONE),
            )
            if (scale < 1) continue
            val area = version.width * version.height * scale * scale
            if (scale < bestScale || (scale == bestScale && area <= bestArea)) continue
            val symbol = encode(text, version) ?: continue
            best = symbol
            bestScale = scale
            bestArea = area
        }
        return best
    }

    /** The symbol for exactly this version, or null when the text does not fit into it. */
    fun encode(text: String, version: RmqrVersion): Symbol? {
        val bits = dataBits(text, version) ?: return null
        return build(interleave(padToCodewords(bits, version), version), version)
    }

    // ----- data -----

    private fun modeFor(text: String): Mode = when {
        text.all { it in '0'..'9' } -> Mode.NUMERIC
        text.all { it in ALPHANUMERIC } -> Mode.ALPHANUMERIC
        else -> Mode.BYTE
    }

    /** Mode, character count and payload, or null once it cannot fit this version. */
    private fun dataBits(text: String, version: RmqrVersion): BitSink? {
        if (text.isEmpty()) return null
        val mode = modeFor(text)
        val bytes = if (mode == Mode.BYTE) text.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val count = if (mode == Mode.BYTE) bytes.size else text.length
        val countBits = when (mode) {
            Mode.NUMERIC -> version.cciNumeric
            Mode.ALPHANUMERIC -> version.cciAlphanumeric
            Mode.BYTE -> version.cciByte
        }
        if (count >= 1 shl countBits) return null

        val out = BitSink()
        out.put(mode.indicator, 3)
        out.put(count, countBits)
        when (mode) {
            Mode.NUMERIC -> text.chunked(3).forEach {
                out.put(it.toInt(), when (it.length) { 3 -> 10; 2 -> 7; else -> 4 })
            }
            Mode.ALPHANUMERIC -> text.chunked(2).forEach {
                val first = ALPHANUMERIC.indexOf(it[0])
                if (it.length == 2) out.put(first * 45 + ALPHANUMERIC.indexOf(it[1]), 11)
                else out.put(first, 6)
            }
            Mode.BYTE -> bytes.forEach { out.put(it.toInt() and 0xFF, 8) }
        }

        val capacity = version.dataCodewords * 8
        if (out.size > capacity) return null
        // The terminator is dropped rather than forced when the symbol is exactly full.
        if (out.size + 3 <= capacity) out.put(0, 3)
        return out
    }

    /** Rounds the bits up to whole codewords and fills the rest with the standard's two pad bytes. */
    private fun padToCodewords(bits: BitSink, version: RmqrVersion): IntArray {
        while (bits.size % 8 != 0) bits.put(0, 1)
        val out = IntArray(version.dataCodewords)
        val written = bits.size / 8
        for (i in 0 until written) out[i] = bits.byteAt(i)
        for (i in written until out.size) out[i] = if ((i - written) % 2 == 0) 0xEC else 0x11
        return out
    }

    // ----- error correction -----

    /**
     * How the codewords are spread over Reed-Solomon blocks. Every block carries the same number of
     * error correction codewords; the data is dealt out evenly and the blocks that get one more
     * come last. That rule reproduces the standard's own table for all 32 versions, which is why
     * only three numbers per version have to be tabulated instead of a full block list.
     */
    private fun blockSizes(version: RmqrVersion): IntArray {
        val count = version.blocks
        val short = version.dataCodewords / count
        val longer = version.dataCodewords % count
        return IntArray(count) { if (it < count - longer) short else short + 1 }
    }

    /** Data codewords first, then the parity, each taken from the blocks in turn. */
    private fun interleave(data: IntArray, version: RmqrVersion): IntArray {
        val sizes = blockSizes(version)
        val eccPerBlock = (version.totalCodewords - version.dataCodewords) / version.blocks
        val encoder = ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256)

        var read = 0
        val blocks = sizes.map { size ->
            val block = IntArray(size + eccPerBlock)
            data.copyInto(block, 0, read, read + size)
            read += size
            encoder.encode(block, eccPerBlock)
            block
        }

        val out = IntArray(version.totalCodewords)
        var write = 0
        for (i in 0 until sizes.max()) {
            blocks.forEachIndexed { b, block -> if (i < sizes[b]) out[write++] = block[i] }
        }
        for (i in 0 until eccPerBlock) {
            blocks.forEachIndexed { b, block -> out[write++] = block[sizes[b] + i] }
        }
        return out
    }

    // ----- the symbol itself -----

    private class Grid(val width: Int, val height: Int) {
        val dark = Array(height) { BooleanArray(width) }
        val taken = Array(height) { BooleanArray(width) }

        fun put(x: Int, y: Int, isDark: Boolean) {
            dark[y][x] = isDark
            taken[y][x] = true
        }

        /** Timing patterns only fill what the other patterns left over. */
        fun putIfFree(x: Int, y: Int, isDark: Boolean) {
            if (!taken[y][x]) put(x, y, isDark)
        }
    }

    private fun build(codewords: IntArray, version: RmqrVersion): Symbol {
        val w = version.width
        val h = version.height
        val grid = Grid(w, h)

        finderPattern(grid)
        subFinderPattern(grid)
        cornerFinderPatterns(grid)
        alignmentPatterns(grid, version)
        timingPatterns(grid, version)
        formatInformation(grid, version)

        // Everything the function patterns left over carries data, and only that is masked.
        val masked = placeData(grid, codewords)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (masked[y][x] && (y / 2 + x / 3) % 2 == 0) grid.dark[y][x] = !grid.dark[y][x]
            }
        }
        return Symbol(version, grid.dark)
    }

    /** The 7x7 eye in the top left corner, with its blank separator. */
    private fun finderPattern(grid: Grid) {
        for (y in 0 until 7) {
            for (x in 0 until 7) {
                grid.put(x, y, y == 0 || y == 6 || x == 0 || x == 6)
            }
        }
        for (y in 2 until 5) for (x in 2 until 5) grid.put(x, y, true)
        for (n in 0 until 8) {
            if (n < grid.height) grid.put(7, n, false)
            if (grid.height >= 9) grid.put(n, 7, false)
        }
    }

    /** The smaller 5x5 eye in the bottom right corner. */
    private fun subFinderPattern(grid: Grid) {
        for (i in 0 until 5) {
            for (j in 0 until 5) {
                val isDark = i == 0 || i == 4 || j == 0 || j == 4
                grid.put(grid.width - j - 1, grid.height - i - 1, isDark)
            }
        }
        grid.put(grid.width - 3, grid.height - 3, true)
    }

    /** The stubs that pin the other two corners. */
    private fun cornerFinderPatterns(grid: Grid) {
        val h = grid.height
        val w = grid.width
        grid.put(0, h - 1, true)
        grid.put(1, h - 1, true)
        grid.put(2, h - 1, true)
        if (h >= 11) {
            grid.put(0, h - 2, true)
            grid.put(1, h - 2, false)
        }
        grid.put(w - 1, 0, true)
        grid.put(w - 2, 0, true)
        grid.put(w - 1, 1, true)
        grid.put(w - 2, 1, false)
    }

    /** Three modules wide, at the top and bottom edge, on the columns the version names. */
    private fun alignmentPatterns(grid: Grid, version: RmqrVersion) {
        for (centre in rmqrAlignmentColumns(version.width)) {
            for (i in 0 until 3) {
                for (j in 0 until 3) {
                    val isDark = i == 0 || i == 2 || j == 0 || j == 2
                    grid.put(centre + j - 1, i, isDark)
                    grid.put(centre + j - 1, grid.height - 1 - i, isDark)
                }
            }
        }
    }

    /** Alternating modules along both long edges and down every alignment column. */
    private fun timingPatterns(grid: Grid, version: RmqrVersion) {
        for (x in 0 until grid.width) {
            val isDark = (x + 1) % 2 == 1
            grid.putIfFree(x, 0, isDark)
            grid.putIfFree(x, grid.height - 1, isDark)
        }
        val columns = intArrayOf(0, grid.width - 1) + rmqrAlignmentColumns(version.width)
        for (y in 0 until grid.height) {
            val isDark = (y + 1) % 2 == 1
            for (x in columns) grid.putIfFree(x, y, isDark)
        }
    }

    /**
     * Version and error correction level, 6 bits with 12 bits of BCH parity behind them. It is
     * written twice, beside each of the two eyes, and each copy carries its own fixed mask so that
     * a blank symbol cannot read as a valid one.
     */
    private fun formatInformation(grid: Grid, version: RmqrVersion) {
        // Level M leaves bit 5 clear; H would set it.
        val info = version.index shl 12 or bch(version.index)

        val left = info xor 0b011111101010110010
        for (n in 0 until 18) {
            grid.put(8 + n / 5, 1 + n % 5, left shr n and 1 == 1)
        }

        val right = info xor 0b100000101001111011
        val originX = grid.width - 8
        val originY = grid.height - 6
        for (n in 0 until 15) {
            grid.put(originX + n / 5, originY + n % 5, right shr n and 1 == 1)
        }
        // The last three bits do not fit that block and sit beside it, on its first row.
        for (n in 15 until 18) {
            grid.put(originX + 3 + (n - 15), originY, right shr n and 1 == 1)
        }
    }

    /** The (18, 6) BCH parity the format information carries. */
    private fun bch(data: Int): Int {
        val generator = 0b1111100100101
        var value = data shl 12
        while (highestBit(value) >= 13) value = value xor (generator shl (highestBit(value) - 13))
        return value
    }

    private fun highestBit(value: Int): Int {
        var bits = 0
        var rest = value
        while (rest != 0) {
            bits++
            rest = rest ushr 1
        }
        return bits
    }

    /**
     * Fills the free modules with the codeword bits, two columns at a time, upwards from the bottom
     * right and turning at each edge. Whatever is left over at the end are the remainder bits, which
     * are blank. Returns which modules were written, because only those get masked.
     */
    private fun placeData(grid: Grid, codewords: IntArray): Array<BooleanArray> {
        val written = Array(grid.height) { BooleanArray(grid.width) }
        var bit = 0
        var x = grid.width - 2
        var y = grid.height - 6
        var step = -1
        while (x >= 0) {
            for (column in intArrayOf(x, x - 1)) {
                if (column < 0 || grid.taken[y][column]) continue
                val isDark = bit < codewords.size * 8 &&
                    codewords[bit / 8] shr (7 - bit % 8) and 1 == 1
                grid.put(column, y, isDark)
                written[y][column] = true
                bit++
            }
            when {
                step < 0 && y == 1 -> { x -= 2; step = 1 }
                step > 0 && y == grid.height - 2 -> { x -= 2; step = -1 }
                else -> y += step
            }
        }
        return written
    }

    /** Collects bits most significant first, the order every field of the symbol is written in. */
    private class BitSink {
        private val bytes = ArrayList<Int>()
        var size = 0
            private set

        fun put(value: Int, bits: Int) {
            for (i in bits - 1 downTo 0) {
                if (size % 8 == 0) bytes.add(0)
                if (value shr i and 1 == 1) {
                    bytes[size / 8] = bytes[size / 8] or (1 shl (7 - size % 8))
                }
                size++
            }
        }

        fun byteAt(index: Int): Int = bytes[index]
    }
}
