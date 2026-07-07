package io.github.toolicious.labler.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Loads an image from a SAF/photo-picker Uri, scales it down (the print head is only
 * 96 dots tall) and returns it as PNG Base64, so it can be stored and exported
 * independently within the template. The actual dithering only happens at render time.
 */
object ImageImport {

    data class Loaded(val pngBase64: String, val width: Int, val height: Int)

    fun load(context: Context, uri: Uri, maxSide: Int = 384): Loaded? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (w == decoded.width && h == decoded.height) decoded
        else Bitmap.createScaledBitmap(decoded, w, h, true)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        Loaded(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP), w, h)
    }.getOrNull()
}
