package io.github.toolicious.labler.render

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.res.ResourcesCompat
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.IconFonts
import io.github.toolicious.labler.model.LabelFont
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the bundled fonts once at app startup and provides the base
 * Typeface per [LabelFont]. Bold/Italic are synthesized by the renderer via
 * Typeface.create(base, style). Without initialization (tests,
 * preview) everything falls back to the system sans.
 *
 * Fonts the user added are held next to the bundled ones, keyed by family name. They arrive
 * later than startup because their files are read off the main thread, hence [revision].
 */
object FontRegistry {

    private val bundled = mutableMapOf<LabelFont, Typeface>()

    /** Bundled icon fonts, keyed by the name that icon elements reference. */
    private val iconFonts = mutableMapOf<String, Typeface>()

    /** Fonts the user added, keyed by the family name that templates reference. */
    private val custom = ConcurrentHashMap<String, Typeface>()

    /**
     * Bumped whenever the custom set changes. Compose reads it where a Typeface influences
     * what is on screen (canvas, thumbnails, chip previews), so adding or removing a font
     * redraws everything that shows it.
     */
    var revision by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        fun load(font: LabelFont, resId: Int) {
            runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()?.let { bundled[font] = it }
        }
        load(LabelFont.OSWALD, R.font.oswald)
        load(LabelFont.ZILLA_SLAB, R.font.zilla_slab)
        load(LabelFont.COMFORTAA, R.font.comfortaa)
        load(LabelFont.CAVEAT, R.font.caveat)
        load(LabelFont.PACIFICO, R.font.pacifico)
        runCatching { ResourcesCompat.getFont(context, R.font.material_icons) }.getOrNull()
            ?.let { iconFonts[IconFonts.MATERIAL] = it }
    }

    /**
     * Typeface an icon element needs, or null when its glyph is plain Unicode and the system font
     * is the right one. An unknown key yields null as well, so an element written by a newer
     * version draws a placeholder glyph rather than bringing the render down.
     */
    fun iconFont(key: String?): Typeface? = key?.let { iconFonts[it] }

    /**
     * Replaces the custom fonts. Called from CustomFontRepository on the main dispatcher,
     * after the files have been read on an IO dispatcher.
     */
    fun setCustom(loaded: Map<String, Typeface>) {
        custom.clear()
        custom.putAll(loaded)
        revision++
    }

    /**
     * Typeface for a text element. An unresolvable [customFamily] falls back to the built-in
     * [font] rather than failing, which is what keeps a template readable while one of its
     * fonts is missing.
     */
    fun base(font: LabelFont, customFamily: String? = null): Typeface =
        customFamily?.let { custom[it] } ?: when (font) {
            LabelFont.SANS -> Typeface.SANS_SERIF
            LabelFont.SERIF -> Typeface.SERIF
            LabelFont.MONO -> Typeface.MONOSPACE
            else -> bundled[font] ?: Typeface.SANS_SERIF
        }
}
