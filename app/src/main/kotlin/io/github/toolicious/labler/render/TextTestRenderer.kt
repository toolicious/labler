package io.github.toolicious.labler.render

import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.MonoImage

/** Fast single-text label (test print); uses the real LabelRenderer. */
object TextTestRenderer {

    fun render(text: String, spec: LabelSpec = LabelSpec()): MonoImage {
        // Written for a 96 dot head, so on a shorter one it comes down with it. Without this
        // the sample overflowed a 30 dot head and printed with its bottoms cut off.
        val fitted = 40f * (spec.printHeightPx.toFloat() / LabelSpec.DEFAULT_ELEMENT_HEAD_DOTS)
            .coerceAtMost(1f)
        var element = TextElement(
            id = "quicktext",
            x = 8f,
            y = 0f,
            text = text.ifBlank { "LaBLEr" },
            fontSizePx = fitted,
            bold = true,
            align = LabelTextAlign.CENTER,
            boxWidthPx = (spec.lengthPx - 16).toFloat(),
        )
        val size = LabelRenderer.measure(element)
        element = element.copy(
            y = ((spec.printHeightPx - size.height) / 2f).coerceAtLeast(0f)
        )
        return LabelRenderer.renderMono(spec, listOf(element))
    }
}
