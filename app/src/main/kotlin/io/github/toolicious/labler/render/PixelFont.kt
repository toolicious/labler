package io.github.toolicious.labler.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import io.github.toolicious.labler.model.LabelFont
import java.util.concurrent.ConcurrentHashMap

/**
 * Bitmap fonts, for text so small that an outline cannot survive being rastered (issue #21).
 *
 * Below roughly ten dots of height there is no room left for a stroke, a gap and a counter, so
 * whatever the rasterizer does with the outline the result is a smudge. A bitmap face does not have
 * that problem because somebody sat down and decided, for that exact height, which dots to set,
 * where to drop a stroke and where to shift one so that an "e" still has its hole. That decision
 * cannot be derived from the outline, it is the content of such a font.
 *
 * The faces come from the BDF sources of the projects themselves, turned into the format below by
 * `tools/bdf2pxf.py`. Nothing is scaled on the way in: a size that is not one of the designed ones
 * is reached by repeating whole dots, never by interpolation, which is what keeps them crisp.
 *
 * Format of a `.pxf` asset, little endian:
 * ```
 * "LPXF"  u8 version  u8 cellWidth  u8 cellHeight  u8 ascent  u16 glyphCount
 * glyphCount x { u16 codePoint, ceil(cellWidth/8) * cellHeight bytes, top row first, MSB left }
 * ```
 * The glyphs are sorted by code point, so a lookup is a binary search.
 */
class PixelFont(
    val cellWidth: Int,
    val cellHeight: Int,
    /** Rows above the baseline, needed to line pixel text up with the rest. */
    val ascent: Int,
    private val codePoints: IntArray,
    private val bitmaps: ByteArray,
) {
    private val stride = (cellWidth + 7) / 8
    private val bytesPerGlyph = stride * cellHeight

    private fun indexOf(codePoint: Int): Int = codePoints.binarySearch(codePoint)

    fun covers(codePoint: Int): Boolean = indexOf(codePoint) >= 0

    /** Whether the glyph sets the dot at [x], [y] of its cell. An unknown character is blank. */
    fun isDark(codePoint: Int, x: Int, y: Int): Boolean {
        val index = indexOf(codePoint)
        if (index < 0) return false
        val byte = bitmaps[index * bytesPerGlyph + y * stride + x / 8].toInt()
        return byte shr (7 - x % 8) and 1 == 1
    }

    companion object {
        fun read(bytes: ByteArray): PixelFont? {
            if (bytes.size < 10 || bytes[0] != 'L'.code.toByte() || bytes[1] != 'P'.code.toByte() ||
                bytes[2] != 'X'.code.toByte() || bytes[3] != 'F'.code.toByte()
            ) return null
            fun u8(at: Int) = bytes[at].toInt() and 0xFF
            if (u8(4) != 1) return null
            val cellWidth = u8(5)
            val cellHeight = u8(6)
            val ascent = u8(7)
            val count = u8(8) or (u8(9) shl 8)
            val stride = (cellWidth + 7) / 8
            val perGlyph = stride * cellHeight
            if (bytes.size < 10 + count * (2 + perGlyph)) return null
            val codePoints = IntArray(count)
            val bitmaps = ByteArray(count * perGlyph)
            var at = 10
            for (i in 0 until count) {
                codePoints[i] = u8(at) or (u8(at + 1) shl 8)
                System.arraycopy(bytes, at + 2, bitmaps, i * perGlyph, perGlyph)
                at += 2 + perGlyph
            }
            return PixelFont(cellWidth, cellHeight, ascent, codePoints, bitmaps)
        }
    }
}

/** A face picked for a wanted height, together with how many dots one of its dots becomes. */
data class FittedPixelFont(val font: PixelFont, val scale: Int) {
    val cellWidth: Int get() = font.cellWidth * scale
    val cellHeight: Int get() = font.cellHeight * scale
    val ascent: Int get() = font.ascent * scale

    /**
     * One line of text as its own dots, opaque black on nothing, for showing a face in the
     * interface. The caller tints it and must draw it unfiltered, or the dots go soft and the
     * picture stops being the argument it is meant to be.
     */
    fun rasterize(text: String): Bitmap? {
        if (text.isEmpty()) return null
        val pixels = IntArray(text.length * cellWidth * cellHeight)
        val width = text.length * cellWidth
        text.forEachIndexed { column, character ->
            for (y in 0 until font.cellHeight) {
                for (x in 0 until font.cellWidth) {
                    if (!font.isDark(character.code, x, y)) continue
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = column * cellWidth + x * scale + dx
                            val py = y * scale + dy
                            pixels[py * width + px] = Color.BLACK
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, cellHeight, Bitmap.Config.ARGB_8888)
    }
}

/**
 * The bundled bitmap faces, loaded from the assets on first use.
 *
 * A family is a list of designed heights. Which one a text gets is worked out from the size the
 * user set, and it is never stretched to meet it: the face whose height times a whole number comes
 * closest without going over wins, so every dot stays square and sharp.
 */
object PixelFonts {

    /** Asset names per family, smallest first. The "b" and "B" faces are the bold ones. */
    private val FILES = mapOf(
        LabelFont.PIXEL_FIXED to listOf(
            "5x7", "5x8", "6x9", "6x10", "6x12", "6x13", "7x13", "7x14", "8x13", "9x15", "10x20",
        ),
        LabelFont.PIXEL_TERMINUS to listOf(
            "ter-u12n", "ter-u14n", "ter-u16n", "ter-u18n", "ter-u20n",
            "ter-u22n", "ter-u24n", "ter-u28n", "ter-u32n",
        ),
    )

    /** Bold counterparts, where the family has one. */
    private val BOLD = mapOf(
        LabelFont.PIXEL_FIXED to mapOf(
            "6x13" to "6x13b", "7x13" to "7x13b", "7x14" to "7x14b",
            "8x13" to "8x13b", "9x15" to "9x15b",
        ),
        LabelFont.PIXEL_TERMINUS to FILES.getValue(LabelFont.PIXEL_TERMINUS)
            .associateWith { it.dropLast(1) + "b" },
    )

    /** How much of a caption line the tallest sample takes up. The rest is breathing room. */
    private const val SAMPLE_OF_LINE = 0.8f

    private val loaded = ConcurrentHashMap<String, PixelFont>()
    private var assets: Context? = null

    fun init(context: Context) {
        assets = context.applicationContext
    }

    fun isPixel(font: LabelFont): Boolean = font in FILES

    private fun face(name: String): PixelFont? {
        loaded[name]?.let { return it }
        val context = assets ?: return null
        val bytes = runCatching {
            context.assets.open("pixelfonts/$name.pxf").use { it.readBytes() }
        }.getOrNull() ?: return null
        val font = PixelFont.read(bytes) ?: return null
        loaded[name] = font
        return font
    }

    /** Rows of the tallest of the smallest faces, the one that has to fit a sample line. */
    private val sampleRows: Int
        get() = FILES.keys.mapNotNull { FILES[it]?.firstOrNull()?.let(::face)?.cellHeight }
            .maxOrNull() ?: 12

    /**
     * The smallest designed face of the family, blown up as far as [maxHeightPx] allows, for
     * showing the family off in the interface.
     *
     * Deliberately not [fit], and deliberately one dot size for every family. At its designed size
     * a face is no coarser than any other small text and the dots that are the whole point would
     * not show. And with the dot shared, the samples differ in height by exactly as much as the
     * faces really do: Fixed is drawn seven rows tall and Terminus twelve, so the chip says by
     * itself which of the two belongs on tiny text. The dot is measured off the tallest of them,
     * so none of the samples can outgrow the line it sits on, and it leaves a little air rather
     * than filling that line to the edge, which read as crowded next to the plain captions.
     */
    fun sample(family: LabelFont, maxHeightPx: Float): FittedPixelFont? {
        val name = FILES[family]?.firstOrNull() ?: return null
        val font = face(name) ?: return null
        val dot = (maxHeightPx * SAMPLE_OF_LINE / sampleRows).toInt().coerceAtLeast(1)
        return FittedPixelFont(font, dot)
    }

    /**
     * The face and whole-number magnification that come closest to [heightPx] without exceeding it.
     * A size below the smallest designed face still gets that face rather than nothing, because a
     * label with no text at all would be worse than one whose text is a little too tall.
     */
    fun fit(family: LabelFont, heightPx: Float, bold: Boolean): FittedPixelFont? =
        fitLine(family, columns = 0, heightPx = heightPx, widthPx = 0f, bold = bold)

    /**
     * The same choice for one line that also has to stay inside [widthPx], with [columns]
     * characters on it. The caption under a bar code is the case: its band is ten to twenty dots
     * tall, but a long number would run past the bars at the face that height alone allows, so the
     * width has to count too. [columns] of zero leaves the width out of it.
     */
    fun fitLine(
        family: LabelFont,
        columns: Int,
        heightPx: Float,
        widthPx: Float,
        bold: Boolean,
    ): FittedPixelFont? {
        val names = FILES[family] ?: return null
        var best: FittedPixelFont? = null
        for (name in names) {
            val file = if (bold) BOLD[family]?.get(name) ?: name else name
            val font = face(file) ?: face(name) ?: continue
            val byHeight = (heightPx / font.cellHeight).toInt()
            val scale =
                if (columns > 0) minOf(byHeight, (widthPx / (columns * font.cellWidth)).toInt())
                else byHeight
            if (scale < 1) {
                if (best == null) best = FittedPixelFont(font, 1)
                continue
            }
            val height = font.cellHeight * scale
            if (best == null || height > best.cellHeight || (height == best.cellHeight && scale < best.scale)) {
                best = FittedPixelFont(font, scale)
            }
        }
        return best
    }
}
