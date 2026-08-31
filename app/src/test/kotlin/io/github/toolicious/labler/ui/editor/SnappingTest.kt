package io.github.toolicious.labler.ui.editor

import androidx.compose.ui.geometry.Rect
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.printer.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SnappingTest {

    private val eps = 0.001f

    // ----- rotatedBounds -----

    @Test
    fun `unrotated bounds are the plain box`() {
        val b = rotatedBounds(10f, 20f, 40f, 8f, 0)
        assertEquals(10f, b.left, eps)
        assertEquals(20f, b.top, eps)
        assertEquals(50f, b.right, eps)
        assertEquals(28f, b.bottom, eps)
    }

    @Test
    fun `half a turn leaves the box where it was`() {
        val b = rotatedBounds(10f, 20f, 40f, 8f, 180)
        assertEquals(10f, b.left, eps)
        assertEquals(20f, b.top, eps)
        assertEquals(50f, b.right, eps)
        assertEquals(28f, b.bottom, eps)
    }

    @Test
    fun `a quarter turn swaps the extents around the same center`() {
        val b = rotatedBounds(10f, 20f, 40f, 8f, 90)
        assertEquals(30f, b.center.x, eps) // 10 + 40/2
        assertEquals(24f, b.center.y, eps) // 20 + 8/2
        assertEquals(8f, b.width, eps)
        assertEquals(40f, b.height, eps)
    }

    @Test
    fun `at 45 degrees both extents are the same and larger than either side`() {
        val b = rotatedBounds(0f, 0f, 40f, 8f, 45)
        val expected = (40f + 8f) * Math.sqrt(2.0).toFloat() / 2f
        assertEquals(expected, b.width, eps)
        assertEquals(expected, b.height, eps)
    }

    // ----- bestSnapAxis -----

    private fun target(line: Float, anchor: SnapAnchor = SnapAnchor.LEADING, ref: Rect? = null) =
        SnapTarget(line, 3f, anchor, ref)

    @Test
    fun `nothing in range does not snap`() {
        assertNull(bestSnapAxis(start = 0f, extent = 10f, targets = listOf(target(100f))))
    }

    @Test
    fun `the leading edge snaps onto the line`() {
        val snap = bestSnapAxis(start = 11f, extent = 20f, targets = listOf(target(10f)))
        assertNotNull(snap)
        assertEquals(-1f, snap!!.shift, eps)
        assertEquals(10f, snap.guide.line, eps)
    }

    @Test
    fun `the center snaps onto the line`() {
        val snap = bestSnapAxis(start = 0f, extent = 20f, targets = listOf(target(11f)))
        assertNotNull(snap)
        assertEquals(1f, snap!!.shift, eps) // center sits at 10, has to move to 11
    }

    @Test
    fun `the trailing edge snaps onto the line`() {
        val snap = bestSnapAxis(start = 0f, extent = 20f, targets = listOf(target(21f)))
        assertNotNull(snap)
        assertEquals(1f, snap!!.shift, eps)
    }

    @Test
    fun `the guide reports the anchor and the reference of the target it picked`() {
        val ref = Rect(4f, 5f, 44f, 13f)
        val snap = bestSnapAxis(
            start = 0f,
            extent = 20f,
            targets = listOf(target(45f), target(21f, SnapAnchor.TRAILING, ref)),
        )
        assertNotNull(snap)
        assertEquals(SnapAnchor.TRAILING, snap!!.guide.anchor)
        assertSame(ref, snap.guide.refBounds)
    }

    @Test
    fun `the tolerance is exclusive`() {
        // Features sit at 0, 5 and 10, so a line at 13 is exactly 3 away from the nearest of them.
        assertNull(bestSnapAxis(0f, 10f, listOf(SnapTarget(13f, 3f, SnapAnchor.LEADING))))
        assertNotNull(bestSnapAxis(0f, 10f, listOf(SnapTarget(12.9f, 3f, SnapAnchor.LEADING))))
        // The label lines are given a wider zone than the element lines.
        assertNotNull(bestSnapAxis(0f, 10f, listOf(SnapTarget(13.9f, 4f, SnapAnchor.LEADING))))
    }

    @Test
    fun `an equally close label line beats an element line`() {
        val ref = Rect(0f, 0f, 10f, 10f)
        val label = target(10f, SnapAnchor.CENTER)
        val element = target(10f, SnapAnchor.LEADING, ref)
        val snap = bestSnapAxis(9f, 20f, listOf(label, element))
        assertNotNull(snap)
        assertNull("the label wins the tie, so no reference box is highlighted", snap!!.guide.refBounds)
        assertEquals(SnapAnchor.CENTER, snap.guide.anchor)
    }

    // ----- target builders -----

    @Test
    fun `a fixed label offers its center and both borders`() {
        val spec = LabelSpec(lengthMm = 40) // 320 px
        val t = labelXTargets(spec)
        assertEquals(listOf(160f, 0f, 320f), t.map { it.line })
        assertEquals(
            listOf(SnapAnchor.CENTER, SnapAnchor.LEADING, SnapAnchor.TRAILING),
            t.map { it.anchor },
        )
        assertEquals("the label is never a reference box", listOf(null, null, null), t.map { it.refBounds })
    }

    @Test
    fun `a variable label offers no border of its own along the tape`() {
        // Both edges follow whatever is dragged, so a border derived from the frame would chase
        // itself. Only the other elements' lines are left, and the tape's height is unaffected.
        val variable = LabelSpec(lengthMm = 40, media = MediaType.CONTINUOUS, autoLength = true)
        assertEquals(emptyList<SnapTarget>(), labelXTargets(variable))
        assertEquals(3, labelYTargets().size)
    }

    @Test
    fun `a variable label offers the center it was given, and nothing else`() {
        // The caller decides whether the center holds still; here it only has to come through as
        // the one line, without either border tagging along.
        val variable = LabelSpec(lengthMm = 40, media = MediaType.CONTINUOUS, autoLength = true)
        val t = labelXTargets(variable, autoCenter = 130f)
        assertEquals(listOf(130f), t.map { it.line })
        assertEquals(listOf(SnapAnchor.CENTER), t.map { it.anchor })
        assertEquals("the label is never a reference box", listOf(null), t.map { it.refBounds })
    }

    @Test
    fun `a manual label offers its lines where its leading edge puts them`() {
        // Its edges are dragged rather than derived, so they hold still and can be snapped to. The
        // element coordinates start 2 mm inside the tape, so the tape starts 16 px before them.
        val manual = LabelSpec(
            lengthMm = 40,
            media = MediaType.CONTINUOUS,
            manualEdges = true,
            leadingMm = 2,
        )
        assertEquals(listOf(144f, -16f, 304f), labelXTargets(manual).map { it.line })
    }

    @Test
    fun `a continuous tape on a fixed length keeps its own lines`() {
        val spec = LabelSpec(lengthMm = 40, media = MediaType.CONTINUOUS)
        assertEquals(listOf(160f, 0f, 320f), labelXTargets(spec).map { it.line })
    }

    @Test
    fun `the tape offers its center and both edges across`() {
        val t = labelYTargets()
        assertEquals(listOf(48f, 0f, 96f), t.map { it.line })
    }

    @Test
    fun `every element contributes three lines per axis, each carrying its box`() {
        val a = Rect(10f, 20f, 50f, 28f)
        val b = Rect(0f, 0f, 4f, 4f)
        val (xt, yt) = elementTargets(listOf(a, b))
        assertEquals(6, xt.size)
        assertEquals(6, yt.size)
        assertEquals(listOf(10f, 30f, 50f, 0f, 2f, 4f), xt.map { it.line })
        assertEquals(listOf(20f, 24f, 28f, 0f, 2f, 4f), yt.map { it.line })
        assertEquals(listOf(a, a, a, b, b, b), xt.map { it.refBounds })
        assertEquals(
            listOf(SnapAnchor.LEADING, SnapAnchor.CENTER, SnapAnchor.TRAILING),
            xt.take(3).map { it.anchor },
        )
    }
}
