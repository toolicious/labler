package io.github.toolicious.labler.ui.editor

import androidx.compose.ui.geometry.Rect
import io.github.toolicious.labler.model.LabelSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Pure snapping math, free of Android and of the view model, so it can be unit tested on the JVM.

private const val LABEL_SNAP_TOL = 4f // label center and borders
private const val ELEM_SNAP_TOL = 3f  // small zone for aligning to other elements

/** Which anchor of the reference the dragged element locked onto, along the guide's own axis. */
enum class SnapAnchor { LEADING, CENTER, TRAILING }

/**
 * One active guide line during a drag, in label coordinates.
 *
 * [refBounds] is the bounding box of the element the line came from, taken at drag start, or null
 * when the reference is the label itself. Carrying the box rather than an id means the canvas
 * highlights exactly the geometry the snap arithmetic used and does not have to measure the element
 * again on every frame. The label deliberately has no box here, because an auto-length tape grows
 * while the drag runs and only the canvas knows the length it is currently drawing.
 */
data class SnapGuide(val line: Float, val anchor: SnapAnchor, val refBounds: Rect? = null)

/** Active snap guide lines during dragging, as label coordinates (null = no guide on that axis). */
data class SnapGuides(val x: SnapGuide? = null, val y: SnapGuide? = null)

/** A line the dragged element can lock onto, plus what produced it. */
internal data class SnapTarget(
    val line: Float,
    val tol: Float,
    val anchor: SnapAnchor,
    val refBounds: Rect? = null,
)

/** How far the dragged element has to move on this axis, plus the guide to show for it. */
internal data class AxisSnap(val shift: Float, val guide: SnapGuide)

/**
 * The label's own lines along the tape.
 *
 * A manual or fixed label has edges that hold still, and they sit where the leading edge puts
 * them: element coordinates start [LabelSpec.leadingPx] inside the tape, so the tape starts at
 * minus that.
 *
 * A variable label has no such edges, both follow whatever is being dragged, so a line derived
 * from the frame would chase itself. Its center is the exception and arrives as [autoCenter], null
 * when even that would move. The two borders never survive it: putting an element flush against
 * one makes that element the outermost, which moves the border it was put on, and there is no
 * position where that settles.
 */
internal fun labelXTargets(spec: LabelSpec, autoCenter: Float? = null): List<SnapTarget> =
    if (spec.lengthIsAuto) {
        listOfNotNull(autoCenter?.let { SnapTarget(it, LABEL_SNAP_TOL, SnapAnchor.CENTER) })
    } else {
        val start = (-spec.leadingPx).toFloat()
        listOf(
            SnapTarget(start + spec.lengthPx / 2f, LABEL_SNAP_TOL, SnapAnchor.CENTER),
            SnapTarget(start, LABEL_SNAP_TOL, SnapAnchor.LEADING),
            SnapTarget(start + spec.lengthPx, LABEL_SNAP_TOL, SnapAnchor.TRAILING),
        )
    }

/** The label's own lines across the tape. The height is fixed, so this never depends on the spec. */
internal fun labelYTargets(): List<SnapTarget> = listOf(
    SnapTarget(LabelSpec.PRINT_HEIGHT_PX / 2f, LABEL_SNAP_TOL, SnapAnchor.CENTER),
    SnapTarget(0f, LABEL_SNAP_TOL, SnapAnchor.LEADING),
    SnapTarget(LabelSpec.PRINT_HEIGHT_PX.toFloat(), LABEL_SNAP_TOL, SnapAnchor.TRAILING),
)

/** Leading edge, center and trailing edge of every other element, as (x targets, y targets). */
internal fun elementTargets(bounds: List<Rect>): Pair<List<SnapTarget>, List<SnapTarget>> {
    val xt = mutableListOf<SnapTarget>()
    val yt = mutableListOf<SnapTarget>()
    bounds.forEach { b ->
        xt += SnapTarget(b.left, ELEM_SNAP_TOL, SnapAnchor.LEADING, b)
        xt += SnapTarget(b.center.x, ELEM_SNAP_TOL, SnapAnchor.CENTER, b)
        xt += SnapTarget(b.right, ELEM_SNAP_TOL, SnapAnchor.TRAILING, b)
        yt += SnapTarget(b.top, ELEM_SNAP_TOL, SnapAnchor.LEADING, b)
        yt += SnapTarget(b.center.y, ELEM_SNAP_TOL, SnapAnchor.CENTER, b)
        yt += SnapTarget(b.bottom, ELEM_SNAP_TOL, SnapAnchor.TRAILING, b)
    }
    return xt to yt
}

/** The three lines a box offers on one axis: leading edge, center, trailing edge. */
internal fun boxFeatures(start: Float, extent: Float): FloatArray =
    floatArrayOf(start, start + extent / 2f, start + extent)

/**
 * [primary] plus the lines of [extra] that are far enough from it to be a line of their own.
 *
 * An element can offer a second box, the one around the ink its glyphs really put on the tape, and
 * on most sides that box sits within a dot or two of the first. Two lines a finger cannot tell
 * apart are not two alignments, they are one alignment that snaps twice, so the closer one is
 * dropped and only a genuinely different line survives.
 */
internal fun mergeTargets(primary: List<SnapTarget>, extra: List<SnapTarget>): List<SnapTarget> =
    primary + extra.filter { e -> primary.none { abs(it.line - e.line) < e.tol } }

/** [mergeTargets] for the lines of the dragged element itself. */
internal fun mergeFeatures(primary: FloatArray, extra: FloatArray): FloatArray =
    primary + extra.filter { e -> primary.none { abs(it - e) < ELEM_SNAP_TOL } }

/**
 * Picks the closest snap target on an axis. [features] are the lines the dragged element offers on
 * that axis, normally the three of [boxFeatures]; it locks any of them onto any target line within
 * that target's tolerance. Returns how far to move and the guide to show.
 *
 * The shift is returned rather than the new position, because a translation is the same number in
 * box coordinates and in element coordinates, so the caller does not have to convert back.
 *
 * Equal distances keep the first target, and the caller puts the label's lines first, so a line an
 * element happens to share with the label border or center is credited to the label.
 */
internal fun bestSnapAxis(features: FloatArray, targets: List<SnapTarget>): AxisSnap? {
    var bestDist = Float.MAX_VALUE
    var bestTarget: SnapTarget? = null
    var bestFeature = 0f
    for (target in targets) {
        for (f in features) {
            val d = abs(f - target.line)
            if (d < target.tol && d < bestDist) {
                bestDist = d
                bestTarget = target
                bestFeature = f
            }
        }
    }
    val target = bestTarget ?: return null
    return AxisSnap(
        shift = target.line - bestFeature,
        guide = SnapGuide(target.line, target.anchor, target.refBounds),
    )
}

/** [bestSnapAxis] for an element that offers nothing but its own box. */
internal fun bestSnapAxis(start: Float, extent: Float, targets: List<SnapTarget>): AxisSnap? =
    bestSnapAxis(boxFeatures(start, extent), targets)

/**
 * Axis-aligned bounding box of [box] turned by [rotation] degrees around ([cx], [cy]).
 *
 * Unlike [rotatedBounds] the pivot is free, which is what a box inside an element needs: an
 * element turns around the center of its own box, so anything drawn inside it travels on a circle
 * around that same point rather than around itself.
 */
internal fun rotatedAround(box: Rect, cx: Float, cy: Float, rotation: Int): Rect {
    if (rotation == 0) return box
    val rad = Math.toRadians(rotation.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val xs = floatArrayOf(box.left, box.right, box.right, box.left)
    val ys = floatArrayOf(box.top, box.top, box.bottom, box.bottom)
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in 0..3) {
        val dx = xs[i] - cx
        val dy = ys[i] - cy
        val rx = cx + dx * c - dy * s
        val ry = cy + dx * s + dy * c
        minX = minOf(minX, rx)
        minY = minOf(minY, ry)
        maxX = maxOf(maxX, rx)
        maxY = maxOf(maxY, ry)
    }
    return Rect(minX, minY, maxX, maxY)
}

/** Axis-aligned bounding box of a [w] x [h] box at ([x], [y]) rotated around its own center. */
internal fun rotatedBounds(x: Float, y: Float, w: Float, h: Float, rotation: Int): Rect {
    val cx = x + w / 2f
    val cy = y + h / 2f
    val rad = Math.toRadians(rotation.toDouble())
    val c = abs(cos(rad)).toFloat()
    val sn = abs(sin(rad)).toFloat()
    val hw = w / 2f * c + h / 2f * sn
    val hh = w / 2f * sn + h / 2f * c
    return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}
