package io.github.toolicious.labler.ui.editor

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.FrameStyle
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.render.FontRegistry
import io.github.toolicious.labler.render.MonoConverter
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Axis-aligned bounding box of an element rotated by an arbitrary angle. */
private fun elementBounds(el: LabelElement): Rect {
    val s = LabelRenderer.measure(el)
    val cx = el.x + s.width / 2f
    val cy = el.y + s.height / 2f
    val rad = Math.toRadians(el.rotation.toDouble())
    val c = abs(cos(rad)).toFloat()
    val sn = abs(sin(rad)).toFloat()
    val hw = s.width / 2f * c + s.height / 2f * sn
    val hh = s.width / 2f * sn + s.height / 2f * c
    return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}

/**
 * Static label area (no zoom/pan): the label is fitted to the width and the
 * border is dark gray. Tapping selects an element, dragging moves it, and the
 * round handle at the bottom right scales it. Dragging out of a double tap moves
 * without snapping. Element coordinates are label pixels.
 *
 * The label is shown print accurate: everything but the selected element is rendered
 * through the real print pipeline and magnified without interpolation, so the editor
 * shows the printed dots instead of a smooth screen resolution image.
 */
@Composable
fun EditorCanvas(
    spec: LabelSpec,
    elements: List<LabelElement>,
    selectedId: String?,
    guides: SnapGuides,
    onSelect: (String?) -> Unit,
    onDragStart: (String, Boolean) -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: (String) -> Unit,
    onResizeBy: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // True while a drag that came out of a double tap is running, so the selection
    // frame can show that this move ignores the snap lines.
    var snapFreeDrag by remember { mutableStateOf(false) }

    // On an auto-length tape the canvas is as long as its content (at least the minimum), so it
    // grows while typing. The editor holds the unresolved placeholders, so this is the design
    // length; the true printed length is shown in the print sheet, which resolves them.
    val labelW = LabelRenderer.effectiveLengthPx(spec, elements).toFloat()
    // Anchored modes lay the label out from its content, not from x = 0, so everything that maps
    // element coordinates onto the canvas has to carry this. Taken from the full element list, not
    // from the subset the raster is built from, or the two would drift apart while dragging.
    val offsetPx = LabelRenderer.contentOffsetPx(spec, elements)
    val labelH = LabelSpec.PRINT_HEIGHT_PX.toFloat()
    // Fixed size (die-cut label) = rounded corners, continuous = hard corners.
    val isDieCut = spec.media == MediaType.DIE_CUT
    val cornerR = 12f // label pixels (~1.5 mm)
    val total = if (boxSize.width > 0 && boxSize.height > 0) {
        min(boxSize.width / labelW, boxSize.height / labelH) * 0.96f
    } else 1f
    val contentTL = Offset(
        (boxSize.width - labelW * total) / 2f,
        (boxSize.height - labelH * total) / 2f,
    )

    // The label as the printer sees it: 1 px per dot, 1 bit, same pipeline as the print preview.
    // The selected element is left out and drawn on top as vector graphics, so it stays readable
    // while it is being edited. That also keeps this bitmap valid while dragging or typing, because
    // only the selected element changes then.
    val others = elements.filter { it.id != selectedId }
    // The revision is a key as well: custom fonts finish loading after startup, and this raster
    // would otherwise keep showing the fallback until the element set happens to change.
    // The length is pinned to the canvas: `others` omits the selected element, so letting the
    // renderer derive it would give a shorter raster than the canvas whenever the selected element
    // is the one defining the length, and it would then be drawn stretched.
    val base = remember(spec, others, labelW, offsetPx, FontRegistry.revision) {
        MonoConverter.toBitmap(LabelRenderer.renderMono(spec, others, labelW.toInt(), offsetPx))
    }
    // Nearest neighbor while magnifying (the normal case) shows the real dot pattern. A label may be
    // up to 500 mm = 4000 dots long and then no longer fits the canvas width; when shrinking, nearest
    // neighbor would swallow whole dot columns, so interpolate instead.
    val basePaint = remember(total < 1f) {
        Paint().apply { isAntiAlias = false; isFilterBitmap = total < 1f }
    }

    // Current values for the gestures, without restarting the detector on every change.
    val elementsState = rememberUpdatedState(elements)
    val selectedIdState = rememberUpdatedState(selectedId)
    val totalState = rememberUpdatedState(total)
    val tlState = rememberUpdatedState(contentTL)
    val offsetState = rememberUpdatedState(offsetPx)

    val background = Color(0xFF3A3A3A)
    val selectionColor = Color(0xFFE53935)
    val guideColor = Color(0xFF2979FF)
    val handleRadiusLabel = 18f

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                var mode = 0 // 0 = nothing, 1 = move, 2 = scale
                detectDragGesturesWithDoubleTap(
                    onStart = { pos, snapFree ->
                        val sc = totalState.value
                        val lp = (pos - tlState.value) / sc - Offset(offsetState.value, 0f)
                        val sel = elementsState.value.find { it.id == selectedIdState.value }
                        val onHandle = sel != null && run {
                            val b = elementBounds(sel)
                            (lp - Offset(b.right, b.bottom)).getDistance() < handleRadiusLabel
                        }
                        when {
                            onHandle && sel != null -> {
                                mode = 2
                                onResizeStart(sel.id)
                            }
                            // An already selected element takes priority when dragging: if the
                            // start point is over it, it gets moved even when another element
                            // lies on top. This lets a background element chosen via chip be
                            // moved. (Tapping still selects the topmost element.)
                            sel != null && hitTest(lp, sel) -> {
                                mode = 1
                                snapFreeDrag = snapFree
                                onDragStart(sel.id, snapFree)
                            }
                            else -> {
                                val hit = elementsState.value.lastOrNull { hitTest(lp, it) }
                                if (hit != null) {
                                    mode = 1
                                    snapFreeDrag = snapFree
                                    onDragStart(hit.id, snapFree)
                                } else {
                                    mode = 0
                                    onSelect(null)
                                }
                            }
                        }
                    },
                    onDrag = { amount ->
                        val d = amount / totalState.value
                        when (mode) {
                            1 -> onDragBy(d)
                            2 -> onResizeBy(d)
                        }
                    },
                    onEnd = {
                        if (mode == 1) onDragEnd() else if (mode == 2) onResizeEnd()
                        mode = 0
                        snapFreeDrag = false
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val lp = (pos - tlState.value) / totalState.value - Offset(offsetState.value, 0f)
                    onSelect(elementsState.value.lastOrNull { hitTest(lp, it) }?.id)
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(background)
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val save = nc.save()
                nc.translate(contentTL.x, contentTL.y)
                nc.scale(total, total)
                // The clip cuts the rounded corners of a die-cut label and keeps an element that
                // sticks out over the label edge from painting onto the gray border.
                if (isDieCut) {
                    val path = android.graphics.Path().apply {
                        addRoundRect(0f, 0f, labelW, labelH, cornerR, cornerR, android.graphics.Path.Direction.CW)
                    }
                    nc.clipPath(path)
                } else {
                    nc.clipRect(0f, 0f, labelW, labelH)
                }
                nc.drawBitmap(base, 0f, 0f, basePaint)
                // Drawn last, so a selected element moves to the front while it is selected and
                // drops back into its place in the stack when it is deselected.
                elements.find { it.id == selectedId }?.let {
                    val inner = nc.save()
                    nc.translate(offsetPx, 0f)
                    LabelRenderer.drawElementInto(nc, it)
                    nc.restoreToCount(inner)
                }
                nc.restoreToCount(save)
            }
            // Frame around the label: fixed size rounded, continuous hard.
            if (isDieCut) {
                drawRoundRect(
                    color = Color(0xFFB0B0B0),
                    topLeft = contentTL,
                    size = Size(labelW * total, labelH * total),
                    cornerRadius = CornerRadius(cornerR * total, cornerR * total),
                    style = Stroke(width = 1f),
                )
            } else {
                drawRect(
                    color = Color(0xFFB0B0B0),
                    topLeft = contentTL,
                    size = Size(labelW * total, labelH * total),
                    style = Stroke(width = 1f),
                )
            }

            // Label frame coordinates, where 0 is the left edge of the tape.
            fun toScreen(lx: Float, ly: Float) = contentTL + Offset(lx * total, ly * total)

            // Element coordinates, which the anchored modes shift against the frame.
            fun elToScreen(lx: Float, ly: Float) = toScreen(lx + offsetPx, ly)

            // Snap guide lines
            val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
            guides.xLine?.let { gx ->
                drawLine(
                    guideColor, elToScreen(gx, 0f), elToScreen(gx, labelH),
                    strokeWidth = 2f, pathEffect = dash
                )
            }
            guides.yLine?.let { gy ->
                drawLine(
                    guideColor, toScreen(0f, gy), toScreen(labelW, gy),
                    strokeWidth = 2f, pathEffect = dash
                )
            }

            // Selection frame and scale handle. While dragging without snapping, the frame
            // switches to the guide color, because no guide lines show up in that mode.
            val sel = elements.find { it.id == selectedId }
            if (sel != null) {
                val frameColor = if (snapFreeDrag) guideColor else selectionColor
                val b = elementBounds(sel)
                drawRect(
                    color = frameColor,
                    topLeft = elToScreen(b.left, b.top),
                    size = Size(b.width * total, b.height * total),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
                )
                // Green anchor line shows the text alignment (growth direction).
                if (sel is TextElement) {
                    val ax = when (sel.align) {
                        LabelTextAlign.LEFT -> b.left
                        LabelTextAlign.CENTER -> (b.left + b.right) / 2f
                        LabelTextAlign.RIGHT -> b.right
                    }
                    drawLine(
                        color = Color(0xFF2ECC71),
                        start = elToScreen(ax, b.top),
                        end = elToScreen(ax, b.bottom),
                        strokeWidth = 3f,
                    )
                }
                val handle = elToScreen(b.right, b.bottom)
                drawCircle(Color.White, radius = 13f, center = handle)
                drawCircle(frameColor, radius = 13f, center = handle, style = Stroke(width = 2.5f))
                drawCircle(frameColor, radius = 4.5f, center = handle)
            }
        }
    }
}

/**
 * Drag detector that also recognises "double tap, then drag": when the finger goes down again
 * within the double tap timeout, the drag is reported with snapFree = true. The tap itself stays
 * with the separate tap detector, which selects the element, so this one only has to notice that
 * a second down followed. [onDrag] receives the raw screen delta, like detectDragGestures does.
 */
private suspend fun PointerInputScope.detectDragGesturesWithDoubleTap(
    onStart: (Offset, Boolean) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
) = awaitEachGesture {
    // The tap detector sits further in and consumes the down, hence requireUnconsumed = false.
    val first = awaitFirstDown(requireUnconsumed = false)
    var start = first
    var snapFree = false
    var overSlop = Offset.Zero
    var slop = awaitTouchSlopOrCancellation(first.id) { change, over ->
        change.consume()
        overSlop = over
    }
    if (slop == null) {
        // The finger came up before the touch slop, so this may be the first tap of a double tap.
        val second = awaitSecondDown(first) ?: return@awaitEachGesture
        snapFree = true
        start = second
        slop = awaitTouchSlopOrCancellation(second.id) { change, over ->
            change.consume()
            overSlop = over
        }
    }
    // A plain double tap without any movement: nothing to drag.
    val dragStart = slop ?: return@awaitEachGesture

    onStart(start.position, snapFree)
    if (overSlop != Offset.Zero) onDrag(overSlop)
    drag(dragStart.id) { change ->
        onDrag(change.positionChange())
        change.consume()
    }
    // A cancelled drag ends like a finished one: the move is already applied element by element.
    onEnd()
}

/** How far apart the two downs of a double tap may be before they count as separate gestures. */
private val DOUBLE_TAP_SLOP = 48.dp

/** Waits a moment for the second down of a double tap, close enough to the first to be one. */
private suspend fun AwaitPointerEventScope.awaitSecondDown(
    first: PointerInputChange,
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
    val minUptime = first.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
    val slop = DOUBLE_TAP_SLOP.toPx()
    var change: PointerInputChange
    do {
        change = awaitFirstDown(requireUnconsumed = false)
    } while (change.uptimeMillis < minUptime)
    // Timing alone is not enough: selecting one element and immediately dragging another would
    // otherwise be read as a double tap and move that second element without snapping.
    change.takeIf { (it.position - first.position).getDistance() <= slop }
}

private fun hitTest(lp: Offset, el: LabelElement): Boolean {
    val s = LabelRenderer.measure(el)
    val cx = el.x + s.width / 2f
    val cy = el.y + s.height / 2f
    // Rotate the tap point back into the element's local (unrotated) frame, then check exactly.
    val rad = Math.toRadians(-el.rotation.toDouble())
    val cs = cos(rad).toFloat()
    val sn = sin(rad).toFloat()
    val dx = lp.x - cx
    val dy = lp.y - cy
    val local = Offset(cx + dx * cs - dy * sn, cy + dx * sn + dy * cs)
    val b = Rect(el.x, el.y, el.x + s.width, el.y + s.height)
    val pad = 6f
    if (el is FrameElement && (el.style == FrameStyle.RECT || el.style == FrameStyle.ROUND_RECT)) {
        // Only the frame stroke is clickable; the empty interior lets clicks
        // pass through to underlying elements.
        val outer = b.inflate(pad)
        val inner = b.deflate(el.strokePx + pad)
        val insideInner = inner.width > 0f && inner.height > 0f && inner.contains(local)
        return outer.contains(local) && !insideInner
    }
    return b.inflate(pad).contains(local)
}
