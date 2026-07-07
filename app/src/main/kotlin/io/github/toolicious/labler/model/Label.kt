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
) {
    val lengthPx: Int get() = lengthMm * Protocol.DOTS_PER_MM

    companion object {
        const val PRINT_HEIGHT_PX = Protocol.HEAD_DOTS

        /** Commercially available die-cut labels for P15/P12 (tape width x length in mm). */
        val PRESETS = listOf(
            12 to 40,
            14 to 30, 14 to 40,
            15 to 30, 15 to 40,
        )
    }
}

enum class LabelTextAlign { LEFT, CENTER, RIGHT }

enum class LabelFont {
    SANS, SERIF, MONO,
    OSWALD, ZILLA_SLAB, COMFORTAA, CAVEAT, PACIFICO,
}

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
    val sizePx: Float = 48f,
    val dither: DitherMode = DitherMode.OUTLINE,
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
