package io.github.toolicious.labler.model

import io.github.toolicious.labler.printer.HeadGeometry
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.PrinterFamily
import io.github.toolicious.labler.printer.PrinterProtocols
import io.github.toolicious.labler.printer.dither.DitherMode
import io.github.toolicious.labler.printer.dither.OutlineMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Coordinate system: label pixels (1 px = 1 printer dot), origin top left, X along the tape,
 * Y across it. Identical in editor, renderer and print.
 *
 * How large a dot is and how many of them fit across the tape belongs to the printer family, so
 * every label carries the [family] it was drawn for and reads its [geometry] from there. A design
 * therefore keeps its own scale instead of following whichever printer happens to be connected.
 */
@Serializable
data class LabelSpec(
    val tapeWidthMm: Int = 12,
    val lengthMm: Int = 40,
    val media: MediaType = MediaType.DIE_CUT,
    /**
     * Two flags rather than one [LengthMode] field, so that a template or a backup written before
     * the manual mode existed still reads correctly: an old file carries autoLength and nothing
     * else, and both defaults land it on the mode it had. Read [lengthMode], never these.
     */
    val autoLength: Boolean = false,
    val manualEdges: Boolean = false,
    /**
     * MANUAL only: where the element coordinates start inside the label, which is what the leading
     * edge moves. Negative once that edge has been pulled in past them, and what lies in front of
     * it prints cut off.
     */
    val leadingMm: Int = 0,
    /**
     * Blank tape the app keeps between the content and a label edge it works out itself: at both
     * ends of a variable label, and where a double tap fits a manual edge to the content. Zero
     * prints flush to the edge, which is what someone trimming to the content exactly wants.
     *
     * In dots rather than millimeters, so half a millimeter is a value like any other, and
     * nothing finer than a dot can be printed anyway.
     */
    val marginPx: Int = DEFAULT_MARGIN_PX,
    /**
     * Printer family this label was designed for. Last in the list and defaulted, so a
     * template, backup or history row written before families existed reads back as the one
     * printer the app used to know.
     */
    val family: PrinterFamily = PrinterFamily.DEFAULT,
) {
    /** Print geometry of [family]: head height, dots per millimeter and the bounds. */
    val geometry: HeadGeometry get() = PrinterProtocols.of(family).geometry

    val lengthPx: Int get() = geometry.mmToDots(lengthMm)
    val leadingPx: Int get() = geometry.mmToDots(leadingMm)
    val marginMm: Float get() = marginPx / geometry.headDotsPerMm

    /** Height of every label of this family, in dots. */
    val printHeightPx: Int get() = geometry.headDots

    /** Paper types the printer this label is for understands. */
    val supportedMedia: Set<MediaType> get() = PrinterProtocols.of(family).supportedMedia

    /**
     * How the length of this label comes about. Die-cut is always FIXED whatever the flags say,
     * because its length is the physical label, dictated by the gap the form feed advances to.
     */
    val lengthMode: LengthMode
        get() = when {
            media != MediaType.CONTINUOUS -> LengthMode.FIXED
            autoLength -> LengthMode.VARIABLE
            manualEdges -> LengthMode.MANUAL
            else -> LengthMode.FIXED
        }

    /** Whether the length follows the content. */
    val lengthIsAuto: Boolean get() = lengthMode == LengthMode.VARIABLE

    /**
     * [this] in [mode], with the leading edge back at zero: changing the mode hands the shift the
     * old one drew with over to the element coordinates (see LabelRenderer.rebasedForMode), and a
     * manual label starts measuring from there.
     */
    fun withLengthMode(mode: LengthMode): LabelSpec = copy(
        autoLength = mode == LengthMode.VARIABLE,
        manualEdges = mode == LengthMode.MANUAL,
        leadingMm = 0,
    )

    companion object {
        /** One millimeter on the head the app was built around, and the stored column default. */
        const val DEFAULT_MARGIN_PX = 8

        /** Bounds for the margin an auto edge keeps from the content, in whole millimeters. */
        const val MIN_MARGIN_MM = 0
        const val MAX_MARGIN_MM = 10

        /**
         * Head height the element defaults further down this file are written for. A family
         * with a shorter head scales them down; see EditorViewModel.fittedToHead.
         */
        const val DEFAULT_ELEMENT_HEAD_DOTS = 96

        /** A blank label for [family], on the tape and paper that family actually has. */
        fun forFamily(family: PrinterFamily): LabelSpec {
            val protocol = PrinterProtocols.of(family)
            val dieCut = MediaType.DIE_CUT in protocol.supportedMedia
            return LabelSpec(
                tapeWidthMm = protocol.geometry.tapeWidthsMm.firstOrNull() ?: 12,
                // Die-cut stock where it exists, continuous tape where it does not. Tape has no
                // length of its own, so it starts out growing with whatever is put on it.
                media = if (dieCut) MediaType.DIE_CUT else MediaType.CONTINUOUS,
                autoLength = !dieCut,
                // A millimeter of this family's own grid, not of the one the default assumes.
                marginPx = protocol.geometry.mmToDots(1),
                family = family,
            )
        }
    }
}

enum class LabelTextAlign { LEFT, CENTER, RIGHT }

enum class LabelFont {
    SANS, SERIF, MONO,
    OSWALD, ZILLA_SLAB, COMFORTAA, CAVEAT, PACIFICO,

    /** Bitmap faces, for text too small for an outline to survive rastering. See PixelFont. */
    PIXEL_FIXED, PIXEL_TERMINUS,
}

/** Icon fonts bundled with the app, as stored in [IconElement.iconFont]. */
object IconFonts {
    const val MATERIAL = "material"
}

/**
 * Where the length of a continuous label comes from: from its content, from the edges the user
 * dragged, or from a number typed in.
 */
enum class LengthMode { VARIABLE, MANUAL, FIXED }

enum class FrameStyle { RECT, ROUND_RECT, LINE_H, LINE_V }

enum class Symbology {
    QR_CODE,

    /** Rectangular Micro QR: the same idea as QR, laid out flat along the tape. See RmqrEncoder. */
    RMQR,

    DATA_MATRIX,

    CODE_128,
    EAN_13,
    UPC_A,
    CODE_39,
    ITF,
    ;

    /**
     * Two-dimensional codes. They carry no caption underneath and their box is theirs to fill,
     * while a bar code has to keep at least one pixel per bar and grows its frame instead.
     */
    val isMatrix: Boolean get() = this == QR_CODE || this == RMQR || this == DATA_MATRIX

    /** Matrix codes that come out square whatever box they are given. rMQR is the flat one. */
    val isSquare: Boolean get() = isMatrix && this != RMQR
}

enum class QrPayloadType { TEXT, LINK, WIFI, EMAIL, PHONE, CONTACT }

@Serializable
sealed interface LabelElement {
    val id: String
    val x: Float
    val y: Float
    val rotation: Int

    fun moved(dx: Float, dy: Float): LabelElement
}

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String,
    override val x: Float = 8f,
    override val y: Float = 24f,
    override val rotation: Int = 0,
    val text: String = "Text",
    val fontSizePx: Float = 32f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val align: LabelTextAlign = LabelTextAlign.LEFT,
    val font: LabelFont = LabelFont.SANS,
    /**
     * Family name of a custom font, or null to use the built-in [font]. The reference is kept
     * even while that font is not installed, so [font] renders as a stand-in and the template
     * repairs itself as soon as the font is added back.
     */
    val customFont: String? = null,
    val boxWidthPx: Float? = null,
) : LabelElement {
    override fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
}

@Serializable
@SerialName("icon")
data class IconElement(
    override val id: String,
    override val x: Float = 8f,
    override val y: Float = 8f,
    override val rotation: Int = 0,
    val glyph: String = "□",
    /**
     * Icon font the glyph belongs to, or null for a plain Unicode symbol in the system font. An
     * icon font keeps its glyphs in the private use area, where a codepoint means nothing without
     * the font it came from, so the element has to remember which one that was. An unknown value
     * falls back to the system font instead of failing, which keeps a template from a newer
     * version readable.
     */
    val iconFont: String? = null,
    val sizePx: Float = 48f,
    val dither: DitherMode = DitherMode.THRESHOLD,
    val contrast: Int = 0,
    val outlineSensitivity: Int = 88,
    val outlineThickness: Int = 1,
    val outlineMethod: OutlineMethod = OutlineMethod.LINES, // symbols default to region-based lines
    val invert: Boolean = false,
    val outlineSmooth: Boolean = false,
) : LabelElement {
    override fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
}

@Serializable
@SerialName("frame")
data class FrameElement(
    override val id: String,
    override val x: Float = 4f,
    override val y: Float = 4f,
    override val rotation: Int = 0,
    val style: FrameStyle = FrameStyle.RECT,
    val widthPx: Float = 120f,
    val heightPx: Float = 88f,
    val strokePx: Float = 2f,
    val cornerRadiusPx: Float = 0f,
) : LabelElement {
    override fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
}

@Serializable
@SerialName("barcode")
data class BarcodeElement(
    override val id: String,
    override val x: Float = 8f,
    override val y: Float = 8f,
    override val rotation: Int = 0,
    val symbology: Symbology = Symbology.QR_CODE,
    val data: String = "",
    val widthPx: Float = 64f,
    val heightPx: Float = 64f,
    val showText: Boolean = true,
    /**
     * Face of the caption, out of the same set a text element offers. The default is what every
     * caption was drawn in before it could be picked, so an older label keeps the look it had.
     */
    val captionFont: LabelFont = LabelFont.SANS,
    /** A font of the user's own, which wins over [captionFont] the way it does on a text element. */
    val captionCustomFont: String? = null,
    /**
     * Height of the caption band in dots, or null for the share of the code the caption always
     * took. Null is the default, so an older label keeps the size it had.
     */
    val captionSizePx: Float? = null,
    // For QR codes only: a typed payload (WiFi, contact, ...). The encoded string in `data` is
    // rebuilt from these fields, which are kept so the wizard can be reopened for editing.
    val payloadType: QrPayloadType = QrPayloadType.TEXT,
    val payload: Map<String, String> = emptyMap(),
) : LabelElement {
    override fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
}

@Serializable
@SerialName("image")
data class ImageElement(
    override val id: String,
    override val x: Float = 8f,
    override val y: Float = 8f,
    override val rotation: Int = 0,
    val pngBase64: String = "",
    val srcWidth: Int = 1,
    val srcHeight: Int = 1,
    val widthPx: Float = 96f,
    val dither: DitherMode = DitherMode.FLOYD_STEINBERG,
    val invert: Boolean = false,
    val threshold: Int = 128,
    val contrast: Int = 0,
    val outlineSensitivity: Int = 88,
    val outlineThickness: Int = 1,
    val outlineMethod: OutlineMethod = OutlineMethod.CANNY, // photos default to gradient edge detection
    val outlineSmooth: Boolean = false,
) : LabelElement {
    /** Display height derived from the width while preserving the aspect ratio. */
    val heightPx: Float get() = if (srcWidth > 0) widthPx * srcHeight / srcWidth else widthPx
    override fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
}

@Serializable
data class LabelTemplate(
    val id: String,
    val name: String,
    val spec: LabelSpec = LabelSpec(),
    val elements: List<LabelElement> = emptyList(),
    val favorite: Boolean = false,
    val counterValue: Int = 1,
    /**
     * Labels printed from this template, copies counted. Defaulted so a backup written before the
     * counter existed still reads, and so an imported template starts over at zero.
     */
    val printCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
