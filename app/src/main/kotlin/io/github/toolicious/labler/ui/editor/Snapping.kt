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
 * The label's own lines along the tape. A variable label has no fixed edge on either side, both
 * follow whatever is being dragged, so all three targets derived from the frame would chase
 * themselves and are left out. The other elements' lines still hold.
 *
 * A manual label does have edges, but they sit where its leading edge puts them: element
 * coordinates start [LabelSpec.leadingPx] inside the tape, so the tape starts at minus that.
 */
internal fun labelXTargets(spec: LabelSpec): List<SnapTarget> =
    if (spec.lengthIsAuto) {
        emptyList()
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

/**
 * Picks the closest snap target on an axis. [start] and [extent] describe the dragged element's
 * bounding box on that axis; it snaps by its leading edge, center, or trailing edge to any target
 * line within that target's tolerance. Returns how far to move and the guide to show.
 *
 * The shift is returned rather than the new position, because a translation is the same number in
 * box coordinates and in element coordinates, so the caller does not have to convert back.
 *
 * Equal distances keep the first target, and the caller puts the label's lines first, so a line an
 * element happens to share with the label border or center is credited to the label.
 */
internal fun bestSnapAxis(start: Float, extent: Float, targets: List<SnapTarget>): AxisSnap? {
    val features = floatArrayOf(start, start + extent / 2f, start + extent)
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
