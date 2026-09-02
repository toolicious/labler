package io.github.toolicious.labler.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.printer.PrinterProtocol
import io.github.toolicious.labler.printer.TestPattern
import io.github.toolicious.labler.printer.Tunable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The geometry test pattern with the settings that produced it printed onto it.
 *
 * A pattern nobody can trace back to its settings is worth little once it has been photographed
 * and sent somewhere: the dot pitch is exactly what such a print is meant to prove, so it belongs
 * on the tape rather than in an accompanying message.
 */
object TestPatternRenderer {

    /** Below this the caption would be too small to read, so it goes beside the pattern instead. */
    private const val MIN_HEAD_FOR_INLINE_CAPTION = 48

    /** Blank dots between the pattern and a caption set beside it. */
    private const val CAPTION_GAP = 16

    fun render(
        protocol: PrinterProtocol,
        lengthDots: Int = TestPattern.DEFAULT_LENGTH_DOTS,
    ): MonoImage {
        val head = protocol.geometry.headDots
        val pattern = MonoConverter.toBitmap(TestPattern.create(protocol.geometry, lengthDots))
        val caption = caption(protocol, lengthDots)
        val inline = head >= MIN_HEAD_FOR_INLINE_CAPTION

        val paint = Paint().apply {
            isAntiAlias = false
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = if (inline) (head / 5f).coerceAtLeast(12f) else head - 6f
        }
        val captionWidth = paint.measureText(caption).roundToInt()

        val width = if (inline) lengthDots else lengthDots + CAPTION_GAP + captionWidth + 4
        val out = Bitmap.createBitmap(width, head, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(pattern, 0f, 0f, null)
        pattern.recycle()

        // Inline it goes into the clear band below the arrow, past the diagonal that crosses the
        // left of the pattern. Beside it, it simply follows the pattern.
        val baseline = if (inline) head - head / 16f - 2f else head / 2f - (paint.ascent() + paint.descent()) / 2f
        val x = if (inline) head + 8f else (lengthDots + CAPTION_GAP).toFloat()
        canvas.drawText(caption, x, baseline, paint)

        val mono = MonoConverter.convert(out, head)
        out.recycle()
        return mono
    }

    /**
     * What the print is worth knowing about itself. The dot pitch first, because that is the value
     * a ruler on this printout is meant to check, then whatever else the family is still unsure of.
     */
    private fun caption(protocol: PrinterProtocol, lengthDots: Int): String {
        val parts = mutableListOf(
            String.format(Locale.US, "%.3f d/mm", protocol.geometry.dotsPerMm),
            "${protocol.geometry.headDots}x$lengthDots",
        )
        protocol.tunables.forEach { tunable ->
            val value = protocol.tunableValue(tunable) ?: return@forEach
            when (tunable) {
                // Already spelled out above, in the units someone measures in.
                Tunable.DOTS_PER_MM, Tunable.HEAD_DOTS -> Unit
                // Does not touch a single dot of the image, so it says nothing about this print.
                Tunable.AWAIT_PRINT_RESULT -> Unit
                Tunable.ROW_BIT_OFFSET -> parts += "off$value"
                Tunable.REVERSE_COLUMN_BYTES -> parts += if (value.toBoolean()) "rev" else "fwd"
            }
        }
        return parts.joinToString(" ")
    }
}
