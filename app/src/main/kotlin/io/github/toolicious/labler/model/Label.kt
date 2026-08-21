package io.github.toolicious.labler.model

import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.Protocol
import io.github.toolicious.labler.printer.dither.DitherMode
import io.github.toolicious.labler.printer.dither.OutlineMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Coordinate system: label pixels (1 px = 1 dot = 0.125 mm), origin top
 * left, X along the tape, Y across (0..95). Identical in editor,
 * renderer and print.
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
    /** MANUAL only: blank tape in front of the leftmost element. */
    val leadingMm: Int = 0,
) {
    val lengthPx: Int get() = lengthMm * Protocol.DOTS_PER_MM
    val leadingPx: Int get() = leadingMm * Protocol.DOTS_PER_MM

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

    /** Whether the label is laid out from its content rather than from the tape start. */
    val contentIsAnchored: Boolean get() = lengthMode != LengthMode.FIXED

    fun withLengthMode(mode: LengthMode): LabelSpec = copy(
        autoLength = mode == LengthMode.VARIABLE,
        manualEdges = mode == LengthMode.MANUAL,
    )

    companion object {
        const val PRINT_HEIGHT_PX = Protocol.HEAD_DOTS

        /** Bounds for a label length in mm, for the fixed value as well as an auto-grown one. */
        const val MIN_LENGTH_MM = 10
        const val MAX_LENGTH_MM = 500
        const val MAX_LENGTH_PX = MAX_LENGTH_MM * Protocol.DOTS_PER_MM

        /** Bounds for the tape width in mm. */
        const val MIN_TAPE_MM = 10
        const val MAX_TAPE_MM = 15

        /** Commercially available die-cut labels for P15/P12 (tape width x length in mm). */
        val PRESETS = listOf(
            12 to 40,
            14 to 30, 14 to 40,
            15 to 30, 15 to 40,
        )

        /** Tape widths available as continuous cartridges, derived from the die-cut stock. */
        val TAPE_WIDTHS = PRESETS.map { it.first }.distinct()
    }
}

enum class LabelTextAlign { LEFT, CENTER, RIGHT }

enum class LabelFont {
    SANS, SERIF, MONO,
    OSWALD, ZILLA_SLAB, COMFORTAA, CAVEAT, PACIFICO,
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

enum class Symbology { QR_CODE, CODE_128, EAN_13, UPC_A, CODE_39, ITF }

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
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
