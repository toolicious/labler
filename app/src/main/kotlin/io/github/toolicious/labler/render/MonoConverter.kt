package io.github.toolicious.labler.render

import android.graphics.Bitmap
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.printer.Protocol

/**
 * Converts a rendered ARGB bitmap into the 1-bit printer image:
 * fixed threshold 128 on the RGB mean, alpha is ignored
 * (the renderer always draws on a white background).
 */
object MonoConverter {

    /** 1-bit image as a black-and-white bitmap (for the pixel-accurate print preview). */
    fun toBitmap(mono: MonoImage): Bitmap {
        val px = IntArray(mono.width * mono.height) { i ->
            if (mono.black[i]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(px, mono.width, mono.height, Bitmap.Config.ARGB_8888)
    }

    fun convert(bitmap: Bitmap): MonoImage {
        require(bitmap.height == Protocol.HEAD_DOTS) {
            "Bitmap height must be ${Protocol.HEAD_DOTS} px, was ${bitmap.height}"
        }
        val w = bitmap.width
        val pixels = IntArray(w * bitmap.height)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, bitmap.height)
        val black = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            black[i] = (r + g + b) / 3 < 128
        }
        return MonoImage(w, black)
    }
}
