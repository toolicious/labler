package io.github.toolicious.labler.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.FrameStyle
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.render.LabelRenderer
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
 * round handle at the bottom right scales it. Element coordinates are label pixels.
 */
@Composable
fun EditorCanvas(
    spec: LabelSpec,
    elements: List<LabelElement>,
    selectedId: String?,
    guides: SnapGuides,
    onSelect: (String?) -> Unit,
    onDragStart: (String) -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: (String) -> Unit,
    onResizeBy: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val labelW = spec.lengthPx.toFloat()
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

    // Current values for the gestures, without restarting the detector on every change.
    val elementsState = rememberUpdatedState(elements)
    val selectedIdState = rememberUpdatedState(selectedId)
    val totalState = rememberUpdatedState(total)
    val tlState = rememberUpdatedState(contentTL)

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
                detectDragGestures(
                    onDragStart = { pos ->
                        val sc = totalState.value
                        val lp = (pos - tlState.value) / sc
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
                                onDragStart(sel.id)
                            }
                            else -> {
                                val hit = elementsState.value.lastOrNull { hitTest(lp, it) }
                                if (hit != null) {
                                    mode = 1
                                    onDragStart(hit.id)
                                } else {
                                    mode = 0
                                    onSelect(null)
                                }
                            }
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val d = amount / totalState.value
                        when (mode) {
                            1 -> onDragBy(d)
                            2 -> onResizeBy(d)
                        }
                    },
                    onDragEnd = {
                        if (mode == 1) onDragEnd() else if (mode == 2) onResizeEnd()
                        mode = 0
                    },
                    onDragCancel = {
                        if (mode == 1) onDragEnd() else if (mode == 2) onResizeEnd()
                        mode = 0
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val lp = (pos - tlState.value) / totalState.value
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
                // Without a clip, LabelRenderer.drawInto (drawColor WHITE) fills the whole
                // canvas white and covers the gray border; therefore limit it to the label.
                if (isDieCut) {
                    val path = android.graphics.Path().apply {
                        addRoundRect(0f, 0f, labelW, labelH, cornerR, cornerR, android.graphics.Path.Direction.CW)
                    }
                    nc.clipPath(path)
                } else {
                    nc.clipRect(0f, 0f, labelW, labelH)
                }
                LabelRenderer.drawInto(nc, spec, elements)
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

            fun toScreen(lx: Float, ly: Float) = contentTL + Offset(lx * total, ly * total)

            // Snap guide lines
            val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
            guides.xLine?.let { gx ->
                drawLine(
                    guideColor, toScreen(gx, 0f), toScreen(gx, labelH),
                    strokeWidth = 2f, pathEffect = dash
                )
            }
            guides.yLine?.let { gy ->
                drawLine(
                    guideColor, toScreen(0f, gy), toScreen(labelW, gy),
                    strokeWidth = 2f, pathEffect = dash
                )
            }

            // Selection frame and scale handle
            val sel = elements.find { it.id == selectedId }
            if (sel != null) {
                val b = elementBounds(sel)
                drawRect(
                    color = selectionColor,
                    topLeft = toScreen(b.left, b.top),
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
                        start = toScreen(ax, b.top),
                        end = toScreen(ax, b.bottom),
                        strokeWidth = 3f,
                    )
                }
                val handle = toScreen(b.right, b.bottom)
                drawCircle(Color.White, radius = 13f, center = handle)
                drawCircle(selectionColor, radius = 13f, center = handle, style = Stroke(width = 2.5f))
                drawCircle(selectionColor, radius = 4.5f, center = handle)
            }
        }
    }
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
