package io.github.toolicious.labler.render

import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LengthMode
import io.github.toolicious.labler.printer.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a variable label lays itself out. Everything is in layout dots, of which there are 8 to
 * the millimeter on this family; what the printer is sent is a stretch of that.
 *
 * Auto length only exists on continuous tape; a die-cut spec is FIXED whatever the flags say.
 */
class AutoLengthTest {

    private fun spec(minMm: Int, marginPx: Int = 8) =
        LabelSpec(lengthMm = minMm, media = MediaType.CONTINUOUS, marginPx = marginPx)
            .withLengthMode(LengthMode.VARIABLE)

    private fun box(id: String, x: Float, w: Float): LabelElement =
        FrameElement(id = id, x = x, y = 0f, widthPx = w, heightPx = 40f)

    /** Label length, and where the content lands on the tape, as the drawing works it out. */
    private fun layout(spec: LabelSpec, elements: List<LabelElement>): String {
        val len = LabelRenderer.effectiveLengthPx(spec, elements)
        val off = LabelRenderer.contentOffsetPx(spec, elements)
        val left = elements.minOf { it.x } + off
        val right = elements.maxOf { it.x + (it as FrameElement).widthPx } + off
        return "len=$len front=${left.toInt()} back=${(len - right).toInt()}"
    }

    @Test
    fun `a lone element can be arranged in the room the minimum length leaves`() {
        val s = spec(minMm = 20)
        // 20 mm label, 10 mm element, 1 mm margins: 8 mm of room, so 8..72 px.
        assertEquals("len=160 front=8 back=72", layout(s, listOf(box("a", 0f, 80f))))
        assertEquals("len=160 front=8 back=72", layout(s, listOf(box("a", 8f, 80f))))
        assertEquals("len=160 front=40 back=40", layout(s, listOf(box("a", 40f, 80f))))
        assertEquals("len=160 front=72 back=8", layout(s, listOf(box("a", 72f, 80f))))
        // Past the room there is nothing left to give, so the label slides along instead.
        assertEquals("len=160 front=72 back=8", layout(s, listOf(box("a", 200f, 80f))))
    }

    @Test
    fun `moving one element does not change the length while there is room`() {
        val s = spec(minMm = 20)
        val lengths = listOf(0f, 8f, 40f, 72f, 200f)
            .map { LabelRenderer.effectiveLengthPx(s, listOf(box("a", it, 80f))) }
        assertEquals(listOf(160, 160, 160, 160, 160), lengths)
    }

    @Test
    fun `a wider element pushes the length past the minimum`() {
        val s = spec(minMm = 20)
        // 22 mm of content plus two margins: 24 mm.
        assertEquals("len=192 front=8 back=8", layout(s, listOf(box("a", 8f, 176f))))
    }

    @Test
    fun `dragging one element outwards takes the other one to the edge, then grows the label`() {
        // 30 mm label, symbol of 8 mm at 11 mm, text of 10 mm dragged to the right edge.
        val s = spec(minMm = 30)
        fun withText(x: Float) = listOf(box("symbol", 88f, 64f), box("text", x, 80f))

        assertEquals("len=240 front=88 back=8", layout(s, withText(152f)))
        // One millimeter to the right: the text stays at the edge, the symbol comes along.
        assertEquals("len=240 front=80 back=8", layout(s, withText(160f)))
        // Ten millimeters in, the symbol has reached the margin and the room is used up.
        assertEquals("len=240 front=8 back=8", layout(s, withText(232f)))
        // From there the label grows by whatever the text is dragged further.
        assertEquals("len=248 front=8 back=8", layout(s, withText(240f)))
        assertEquals("len=320 front=8 back=8", layout(s, withText(312f)))
    }

    @Test
    fun `the length follows the content by the dot, not by the millimeter`() {
        val s = spec(minMm = 10)
        // 100 dots of content plus two margins is 116, and 116 it stays: rounding that up to the
        // next whole millimeter is what made the label jump while its content was dragged.
        assertEquals("len=116 front=8 back=8", layout(s, listOf(box("a", 8f, 100f))))
        assertEquals("len=117 front=8 back=8", layout(s, listOf(box("a", 8f, 101f))))
    }

    @Test
    fun `a margin of zero trims the label to the content`() {
        val s = spec(minMm = 10, marginPx = 0)
        assertEquals("len=176 front=0 back=0", layout(s, listOf(box("a", 40f, 176f))))
    }
}
