package io.github.toolicious.labler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import io.github.toolicious.labler.model.LabelFont
import io.github.toolicious.labler.render.FontRegistry

/**
 * FontFamily for showing a label font in the UI, so a chip or a list row is set in the very font
 * it stands for. Reads FontRegistry.revision, so it recomposes once fonts finish loading or when
 * the user adds or removes one. An unresolvable [customFamily] yields the fallback typeface, the
 * same one the label itself would render with.
 */
@Composable
fun labelFontFamily(font: LabelFont = LabelFont.SANS, customFamily: String? = null): FontFamily {
    val revision = FontRegistry.revision
    return remember(font, customFamily, revision) {
        FontFamily(Typeface(FontRegistry.base(font, customFamily)))
    }
}

/**
 * FontFamily for showing a glyph out of a bundled icon font, so the picker and the property panel
 * display the icon itself instead of the empty box its private use codepoint would otherwise give.
 * A [key] with no font behind it yields the default family.
 *
 * Unlike [labelFontFamily] this does not watch FontRegistry.revision, because the icon fonts are
 * bundled and loaded while the application starts, before any of this is on screen. Were one ever
 * to arrive late, this would keep handing out the default family for good.
 */
@Composable
fun iconFontFamily(key: String?): FontFamily = remember(key) {
    FontRegistry.iconFont(key)?.let { FontFamily(Typeface(it)) } ?: FontFamily.Default
}
