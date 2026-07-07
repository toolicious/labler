package io.github.toolicious.labler.render

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.LabelFont

/**
 * Loads the bundled fonts once at app startup and provides the base
 * Typeface per [LabelFont]. Bold/Italic are synthesized by the renderer via
 * Typeface.create(base, style). Without initialization (tests,
 * preview) everything falls back to the system sans.
 */
object FontRegistry {

    private val bundled = mutableMapOf<LabelFont, Typeface>()

    fun init(context: Context) {
        fun load(font: LabelFont, resId: Int) {
            runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()?.let { bundled[font] = it }
        }
        load(LabelFont.OSWALD, R.font.oswald)
        load(LabelFont.ZILLA_SLAB, R.font.zilla_slab)
        load(LabelFont.COMFORTAA, R.font.comfortaa)
        load(LabelFont.CAVEAT, R.font.caveat)
        load(LabelFont.PACIFICO, R.font.pacifico)
    }

    fun base(font: LabelFont): Typeface = when (font) {
        LabelFont.SANS -> Typeface.SANS_SERIF
        LabelFont.SERIF -> Typeface.SERIF
        LabelFont.MONO -> Typeface.MONOSPACE
        else -> bundled[font] ?: Typeface.SANS_SERIF
    }
}
