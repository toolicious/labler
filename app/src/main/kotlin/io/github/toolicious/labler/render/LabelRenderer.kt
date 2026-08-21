package io.github.toolicious.labler.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import io.github.toolicious.labler.model.BarcodeElement
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.FrameStyle
import io.github.toolicious.labler.model.IconElement
import io.github.toolicious.labler.model.ImageElement
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.Symbology
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.printer.Protocol
import io.github.toolicious.labler.printer.dither.Contrast
import io.github.toolicious.labler.printer.dither.Canny
import io.github.toolicious.labler.printer.dither.DitherMode
import io.github.toolicious.labler.printer.dither.OutlineMethod
import io.github.toolicious.labler.printer.dither.Ditherer
import io.github.toolicious.labler.printer.dither.Outline
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Renders a label 1:1 at print resolution (lengthPx x 96, white background).
 * Used identically for the editor preview, thumbnails and printing (WYSIWYG);
 * the 1-bit quantization is handled by the MonoConverter.
 */
object LabelRenderer {

    data class ElementSize(val width: Float, val height: Float)

    // Cache the expensive per-element results so editor redraws (drag, typing, recomposition) do not
    // recompute them every frame. Keyed by everything that affects the output; LruCache is thread-safe.
    private val matrixCache = LruCache<String, BitMatrix>(16)
    private val imageCache = LruCache<String, IntArray>(8)

    // Nearest neighbor, so rotating does not blur the 1-bit rasters and magnifying them in the
    // editor shows the real dots.
    private val nnPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

    /**
     * Trailing margin on an auto-length label, mirroring the 8 px inset that new elements start
     * at, so the content does not end flush with the cut edge.
     */
    const val AUTO_LENGTH_MARGIN_PX = 8f

    /**
     * Right edge of an element in label px. Elements are rotated around their own center
     * (see [drawElementInto]), so a rotated one reaches further than its plain width.
     */
    private fun rightEdge(element: LabelElement): Float {
        val size = measure(element)
        if (element.rotation == 0) return element.x + size.width
        val rad = Math.toRadians(element.rotation.toDouble())
        val halfWidth = (abs(cos(rad)) * size.width + abs(sin(rad)) * size.height) / 2.0
        return element.x + size.width / 2f + halfWidth.toFloat()
    }

    /**
     * The length the label is actually rendered and printed at.
     *
     * A fixed label always uses its own length. An auto-length tape grows to hold its content
     * but never falls below [LabelSpec.lengthMm], which acts as the minimum there. The result is
     * rounded up to a whole millimetre, so the length shown to the user is exact rather than a
     * fraction, and so a job stays on the dot grid.
     */
    fun effectiveLengthPx(spec: LabelSpec, elements: List<LabelElement>): Int {
        if (!spec.lengthIsAuto) return spec.lengthPx
        val content = elements.maxOfOrNull { rightEdge(it) } ?: 0f
        val contentMm = ceil((content + AUTO_LENGTH_MARGIN_PX) / Protocol.DOTS_PER_MM).toInt()
        // coerceAtMost rather than coerceIn, so an out-of-range minimum from hand-edited JSON
        // caps out instead of throwing.
        return maxOf(spec.lengthMm, contentMm)
            .coerceAtMost(LabelSpec.MAX_LENGTH_MM) * Protocol.DOTS_PER_MM
    }

    /** [effectiveLengthPx] in whole millimetres, for the length shown in the UI. */
    fun effectiveLengthMm(spec: LabelSpec, elements: List<LabelElement>): Int =
        effectiveLengthPx(spec, elements) / Protocol.DOTS_PER_MM

    /**
     * @param lengthPx overrides the length instead of deriving it from [elements]. Needed when the
     *   caller renders a subset (the editor omits the selected element and draws it on top), where
     *   deriving would give a shorter label than the one the subset belongs to.
     */
    fun render(
        spec: LabelSpec,
        elements: List<LabelElement>,
        lengthPx: Int = effectiveLengthPx(spec, elements),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(lengthPx, LabelSpec.PRINT_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        drawInto(Canvas(bmp), spec, elements)
        return bmp
    }

    fun renderMono(
        spec: LabelSpec,
        elements: List<LabelElement>,
        lengthPx: Int = effectiveLengthPx(spec, elements),
    ): MonoImage {
        val bmp = render(spec, elements, lengthPx)
        val mono = MonoConverter.convert(bmp)
        bmp.recycle()
        return mono
    }

    /** Draws onto an arbitrary Canvas in label pixel coordinates (for the editor). */
    fun drawInto(canvas: Canvas, spec: LabelSpec, elements: List<LabelElement>) {
        canvas.drawColor(Color.WHITE)
        elements.forEach { drawElementInto(canvas, it) }
    }

    fun measure(element: LabelElement): ElementSize = when (element) {
        is TextElement -> textLayout(element).let { ElementSize(it.width.toFloat(), it.height.toFloat()) }
        is IconElement -> ElementSize(element.sizePx, element.sizePx)
        is FrameElement -> ElementSize(element.widthPx, element.heightPx)
        is BarcodeElement -> {
            // The reserved box: the code renders as large as cleanly fits and centers inside it.
            // A 1D barcode cannot shrink below 1 px per module, so let the frame grow to contain it.
            if (element.symbology == Symbology.QR_CODE) {
                ElementSize(element.widthPx, element.heightPx)
            } else {
                val m = barcodeMatrix(element)
                val w = if (m != null) maxOf(element.widthPx, m.width.toFloat()) else element.widthPx
                ElementSize(w, element.heightPx)
            }
        }
        is ImageElement -> ElementSize(element.widthPx, element.heightPx)
    }

    /**
     * Shifts resolved text elements so that their alignment anchor
     * (left edge / center / right edge) stays fixed when the resolved
     * text has a different width than the original (e.g. after {var:...}).
     * `original` and `resolved` must have the same order/length.
     */
    fun reanchor(original: List<LabelElement>, resolved: List<LabelElement>): List<LabelElement> {
        if (original.size != resolved.size) return resolved
        return resolved.mapIndexed { i, el ->
            val orig = original[i]
            if (el is TextElement && orig is TextElement && el.align != LabelTextAlign.LEFT) {
                val delta = measure(el).width - measure(orig).width
                val dx = when (el.align) {
                    LabelTextAlign.CENTER -> -delta / 2f
                    LabelTextAlign.RIGHT -> -delta
                    LabelTextAlign.LEFT -> 0f
                }
                el.copy(x = el.x + dx)
            } else {
                el
            }
        }
    }

    /**
     * Snaps a coordinate to a whole label pixel (dot). Everything that prints as a 1-bit raster
     * (icon, image, code, frame) has to sit on the dot grid. On the 1:1 print canvas Skia snaps it
     * anyway, but the editor draws the selected element onto a magnified canvas, where a fractional
     * position lands up to one dot next to the rendered label underneath and the element would
     * visibly jump when it is selected.
     */
    private fun snap(v: Float): Float = round(v)

    /**
     * Draws a single element, rotated around its own center, without touching the background.
     * The editor uses this to draw the currently selected element on top of the rendered label.
     */
    fun drawElementInto(canvas: Canvas, element: LabelElement) {
        val size = measure(element)
        canvas.save()
        if (element.rotation != 0) {
            canvas.rotate(
                element.rotation.toFloat(),
                element.x + size.width / 2f,
                element.y + size.height / 2f
            )
        }
        when (element) {
            is TextElement -> drawText(canvas, element)
            is IconElement -> drawIcon(canvas, element)
            is FrameElement -> drawFrame(canvas, element)
            is BarcodeElement -> drawBarcode(canvas, element)
            is ImageElement -> drawImage(canvas, element)
        }
        canvas.restore()
    }

    // ----- Text -----

    private fun textPaint(e: TextElement): TextPaint = TextPaint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = e.fontSizePx
        val base = FontRegistry.base(e.font, e.customFont)
        val style = when {
            e.bold && e.italic -> Typeface.BOLD_ITALIC
            e.bold -> Typeface.BOLD
            e.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        typeface = Typeface.create(base, style)
        isUnderlineText = e.underline
    }

    fun textLayout(e: TextElement): StaticLayout {
        val paint = textPaint(e)
        val width = e.boxWidthPx?.toInt()
            ?: e.text.split('\n').maxOf { ceil(paint.measureText(it)).toInt() }.coerceAtLeast(1)
        val alignment = when (e.align) {
            LabelTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            LabelTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            LabelTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder
            .obtain(e.text, 0, e.text.length, paint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
    }

    private fun drawText(canvas: Canvas, e: TextElement) {
        canvas.save()
        canvas.translate(e.x, e.y)
        textLayout(e).draw(canvas)
        canvas.restore()
    }

    // ----- Icon (emoji/symbol, dithered) -----

    private fun drawIcon(canvas: Canvas, e: IconElement) {
        val size = e.sizePx.toInt().coerceAtLeast(4)
        val off = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val oc = Canvas(off)
        // No white background: keep it transparent so the emoji shape can
        // later be separated from the empty area via the alpha channel.
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = size * 0.85f
            FontRegistry.iconFont(e.iconFont)?.let { typeface = it }
        }
        val layout = StaticLayout.Builder
            .obtain(e.glyph, 0, e.glyph.length, paint, size)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        oc.translate(0f, ((size - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(oc)

        val px = IntArray(size * size)
        off.getPixels(px, 0, size, 0, 0, size, size)
        off.recycle()
        val isGlyph = BooleanArray(px.size)
        // Compute grayscale only for the dither modes; the outline mode works directly on px.
        val outline = e.dither == DitherMode.OUTLINE
        val gray = if (outline) FloatArray(0) else FloatArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            // Only sufficiently opaque pixels count toward the emoji. Semi-transparent
            // edges (e.g. the soft glow around flames) are discarded,
            // because in a single color they would otherwise turn into an ugly raster.
            isGlyph[i] = a >= 128
            if (!outline) {
                val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3f
                gray[i] = (lum * a + 255f * (255 - a)) / 255f
            }
        }
        val black = if (outline) {
            // Outline detects edges via the color difference (not just brightness) directly on the
            // original pixels. The contrast slider does not apply here; the sensitivity controls it.
            val edge = when (e.outlineMethod) {
                OutlineMethod.CANNY -> Canny.detect(px, isGlyph, size, size, e.outlineSensitivity, e.outlineThickness, smooth = e.outlineSmooth)
                OutlineMethod.LINES -> Outline.trace(px, isGlyph, size, size, e.outlineSensitivity, e.outlineThickness, smooth = e.outlineSmooth)
            }
            // Invert an outline = negative: fill the glyph and leave the lines blank.
            if (e.invert) BooleanArray(edge.size) { isGlyph[it] && !edge[it] } else edge
        } else {
            // Apply contrast only to the emoji/symbol shape; the background stays white. Invert flips
            // the glyph tones before dithering.
            val adjusted = Contrast.adjust(gray, e.contrast)
            for (i in adjusted.indices) {
                if (!isGlyph[i]) adjusted[i] = 255f else if (e.invert) adjusted[i] = 255f - adjusted[i]
            }
            Ditherer.of(e.dither).dither(adjusted, size, size)
        }
        val out = IntArray(px.size) { if (black[it] && isGlyph[it]) Color.BLACK else Color.TRANSPARENT }
        val mono = Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(mono, snap(e.x), snap(e.y), nnPaint)
        mono.recycle()
    }

    // ----- Frame -----

    private fun drawFrame(canvas: Canvas, e: FrameElement) {
        val paint = Paint().apply {
            isAntiAlias = false
            color = Color.BLACK
            strokeWidth = e.strokePx
            style = Paint.Style.STROKE
        }
        // Snapped edges plus the whole pixel stroke width put the stroke band on whole dots.
        val left = snap(e.x)
        val top = snap(e.y)
        val right = snap(e.x + e.widthPx)
        val bottom = snap(e.y + e.heightPx)
        val half = e.strokePx / 2f
        when (e.style) {
            FrameStyle.RECT, FrameStyle.ROUND_RECT -> canvas.drawRoundRect(
                left + half, top + half, right - half, bottom - half,
                e.cornerRadiusPx, e.cornerRadiusPx, paint
            )
            FrameStyle.LINE_H -> {
                paint.style = Paint.Style.FILL
                canvas.drawRect(left, top, right, top + e.strokePx, paint)
            }
            FrameStyle.LINE_V -> {
                paint.style = Paint.Style.FILL
                canvas.drawRect(left, top, left + e.strokePx, bottom, paint)
            }
        }
    }

    // ----- Barcode / QR (ZXing, integer module scaling -> no staircase effect) -----

    private fun barcodeCaptionHeight(e: BarcodeElement): Float =
        if (e.symbology != Symbology.QR_CODE && e.showText) (e.heightPx * 0.26f).coerceIn(10f, 20f) else 0f

    private fun barcodeMatrix(e: BarcodeElement): BitMatrix? {
        if (e.data.isBlank()) return null
        // showText changes the encoded height (it reserves a caption band), so it must be in the key.
        val key = "${e.symbology}:${e.widthPx.toInt()}:${e.heightPx.toInt()}:${e.showText}:${e.data}"
        matrixCache.get(key)?.let { return it }
        val isQr = e.symbology == Symbology.QR_CODE
        val format = when (e.symbology) {
            Symbology.QR_CODE -> BarcodeFormat.QR_CODE
            Symbology.CODE_128 -> BarcodeFormat.CODE_128
            Symbology.EAN_13 -> BarcodeFormat.EAN_13
            Symbology.UPC_A -> BarcodeFormat.UPC_A
            Symbology.CODE_39 -> BarcodeFormat.CODE_39
            Symbology.ITF -> BarcodeFormat.ITF
        }
        val side = minOf(e.widthPx, e.heightPx)
        val codeW = (if (isQr) side else e.widthPx).toInt().coerceAtLeast(8)
        val codeH = (if (isQr) side else e.heightPx - barcodeCaptionHeight(e)).toInt().coerceAtLeast(8)
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN, if (isQr) 1 else 2)
            // Without this hint ZXing encodes in ISO-8859-1 and silently turns every character it
            // cannot map into a literal '?', which is what the scanner then reads back. UTF-8 is
            // requested only when the content needs it: ZXing adds an ECI marker for it, and pure
            // ASCII codes keep the exact module pattern they had before.
            if (isQr && e.data.any { it.code > 127 }) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = try {
            if (isQr) QRCodeWriter().encode(e.data, format, codeW, codeH, hints)
            else MultiFormatWriter().encode(e.data, format, codeW, codeH, hints)
        } catch (t: Throwable) {
            null
        }
        if (matrix != null) matrixCache.put(key, matrix)
        return matrix
    }

    private fun drawBarcode(canvas: Canvas, e: BarcodeElement) {
        val matrix = barcodeMatrix(e)
        if (matrix == null) {
            drawCodePlaceholder(canvas, e)
            return
        }
        val mw = matrix.width
        val mh = matrix.height
        val px = IntArray(mw * mh) { i -> if (matrix.get(i % mw, i / mw)) Color.BLACK else Color.TRANSPARENT }
        val bmp = Bitmap.createBitmap(px, mw, mh, Bitmap.Config.ARGB_8888)
        // Center the code inside its reserved box (whitespace around it); the caption sits in the
        // bottom band. The frame is at least as wide as the bars, so a 1D barcode is never clipped.
        val frameW = maxOf(e.widthPx, mw.toFloat())
        val captionH = barcodeCaptionHeight(e)
        val codeAreaH = (e.heightPx - captionH).coerceAtLeast(mh.toFloat())
        val ox = snap(e.x + (frameW - mw) / 2f)
        val oy = snap(e.y + (codeAreaH - mh) / 2f)
        canvas.drawBitmap(bmp, ox, oy, nnPaint)
        bmp.recycle()
        if (captionH > 0f) {
            val tp = TextPaint().apply {
                isAntiAlias = true
                color = Color.BLACK
                textSize = captionH * 0.8f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(e.data, e.x + frameW / 2f, e.y + codeAreaH + captionH * 0.75f, tp)
        }
    }

    private fun drawCodePlaceholder(canvas: Canvas, e: BarcodeElement) {
        val p = Paint().apply {
            isAntiAlias = false
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val left = snap(e.x)
        val top = snap(e.y)
        val right = snap(e.x + e.widthPx)
        val bottom = snap(e.y + e.heightPx)
        canvas.drawRect(left, top, right, bottom, p)
        canvas.drawLine(left, top, right, bottom, p)
        canvas.drawLine(right, top, left, bottom, p)
    }

    // ----- Image (imported, dithered) -----

    private fun drawImage(canvas: Canvas, e: ImageElement) {
        if (e.pngBase64.isBlank()) return
        val w = e.widthPx.toInt().coerceAtLeast(4)
        val h = e.heightPx.toInt().coerceAtLeast(4)
        val out = imagePixels(e, w, h) ?: return
        val bmp = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(bmp, snap(e.x), snap(e.y), nnPaint)
        bmp.recycle()
    }

    /**
     * The 1-bit ARGB pixels for an image element, cached. The heavy pipeline (Base64 decode, rescale,
     * dithering / edge detection) only re-runs when the image or a processing parameter changes, so a
     * plain redraw (dragging any element, typing) reuses the result instead of recomputing it.
     */
    private fun imagePixels(e: ImageElement, w: Int, h: Int): IntArray? {
        val key = "${e.pngBase64.hashCode()}:${e.pngBase64.length}:$w:$h:${e.dither}:${e.invert}:" +
            "${e.threshold}:${e.contrast}:${e.outlineMethod}:${e.outlineSensitivity}:${e.outlineThickness}:${e.outlineSmooth}"
        imageCache.get(key)?.let { return it }
        val src = try {
            val bytes = Base64.decode(e.pngBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            null
        } ?: return null
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        src.recycle()
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        scaled.recycle()
        val isGlyph = BooleanArray(px.size) { (px[it] ushr 24) >= 128 }
        val black = if (e.dither == DitherMode.OUTLINE) {
            val edge = when (e.outlineMethod) {
                OutlineMethod.CANNY -> Canny.detect(px, isGlyph, w, h, e.outlineSensitivity, e.outlineThickness, smooth = e.outlineSmooth)
                OutlineMethod.LINES -> Outline.trace(px, isGlyph, w, h, e.outlineSensitivity, e.outlineThickness, borderIsBackground = false, smooth = e.outlineSmooth)
            }
            // Invert an outline = negative: fill the area and leave the lines blank. (Color-inverting the
            // input before edge detection would not change the edges at all.)
            if (e.invert) BooleanArray(edge.size) { isGlyph[it] && !edge[it] } else edge
        } else {
            // Grayscale (alpha onto white), optionally inverted, dithered at the final display size.
            val gray = FloatArray(px.size) { i ->
                val p = px[i]
                val a = p ushr 24
                val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3f
                val g = (lum * a + 255f * (255 - a)) / 255f
                if (e.invert) 255f - g else g
            }
            when (e.dither) {
                DitherMode.THRESHOLD -> BooleanArray(gray.size) { gray[it] < e.threshold }
                else -> Ditherer.of(e.dither).dither(Contrast.adjust(gray, e.contrast), w, h)
            }
        }
        // Mask by isGlyph so a transparent background never prints ink (e.g. after Invert).
        val out = IntArray(w * h) { if (black[it] && isGlyph[it]) Color.BLACK else Color.TRANSPARENT }
        imageCache.put(key, out)
        return out
    }
}
