package io.github.toolicious.labler.render

import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.MonoImage

/** Fast single-text label (test print); uses the real LabelRenderer. */
object TextTestRenderer {

    fun render(text: String, spec: LabelSpec = LabelSpec()): MonoImage {
        var element = TextElement(
            id = "quicktext",
            x = 8f,
            y = 0f,
            text = text.ifBlank { "LaBLEr" },
            fontSizePx = 40f,
            bold = true,
            align = LabelTextAlign.CENTER,
            boxWidthPx = (spec.lengthPx - 16).toFloat(),
        )
        val size = LabelRenderer.measure(element)
        element = element.copy(
            y = ((LabelSpec.PRINT_HEIGHT_PX - size.height) / 2f).coerceAtLeast(0f)
        )
        return LabelRenderer.renderMono(spec, listOf(element))
    }
}
