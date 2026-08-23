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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.FrameStyle
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.LengthMode
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.render.FontRegistry
import io.github.toolicious.labler.render.MonoConverter
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The two edges of a manual length label. Each is a dashed line beside the tape carrying a flag
 * below it, which points inwards and names the edge. In dp, because a finger is the same size
 * whatever the screen density is.
 */
private val EDGE_LINE_GRAB = 7.dp   // half the band around a line that still counts as the line
private val EDGE_LINE_WIDTH = 3.dp  // in dp, so the line does not thin out on a dense screen
private val EDGE_DASH_ON = 7.dp
private val EDGE_DASH_OFF = 5.dp
private val EDGE_FLAG_HEIGHT = 22.dp
private val EDGE_FLAG_PAD = 9.dp
private val EDGE_FLAG_TEXT = 12.sp
private val EDGE_FLAG_ARROW_GAP = 5.dp  // between the word and the arrow beside it

/**
 * Axis-aligned bounding box of an element rotated by an arbitrary angle. This is the box the canvas
 * outlines and the box the view model snaps against, so a guide always lands on an edge the user
 * can actually see.
 */
internal fun elementBounds(el: LabelElement): Rect {
    val s = LabelRenderer.measure(el)
    return rotatedBounds(el.x, el.y, s.width, s.height, el.rotation)
}

/**
 * Static label area (no zoom/pan): the label is fitted to the width and the
 * border is dark gray. Tapping selects an element, dragging moves it, and the
 * round handle at the bottom right scales it. Dragging out of a double tap moves
 * without snapping. Element coordinates are label pixels.
 *
 * A manual length label additionally gets a handle for each of its two edges, sitting in the gray
 * border next to the tape: dragging one moves that edge, double tapping it pulls the edge up to
 * the content.
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
    onEdgeDragStart: (LabelEdge) -> Unit = {},
    onEdgeDragBy: (Float) -> Unit = {},
    onEdgeDragEnd: () -> Unit = {},
    onEdgeFit: (LabelEdge) -> Unit = {},
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
    // Only a manual length label has edges to drag. Its handles sit next to the tape and need gray
    // border to sit in, so the tape is fitted into a narrower box then. The other modes are laid
    // out exactly as before.
    val isManual = spec.lengthMode == LengthMode.MANUAL
    val density = LocalDensity.current
    val grabPx = with(density) { EDGE_LINE_GRAB.toPx() }
    val flagHeight = with(density) { EDGE_FLAG_HEIGHT.toPx() }
    val flagPad = with(density) { EDGE_FLAG_PAD.toPx() }
    val flagTextPx = with(density) { EDGE_FLAG_TEXT.toPx() }
    val edgeLineWidth = with(density) { EDGE_LINE_WIDTH.toPx() }
    val edgeDash = with(density) { floatArrayOf(EDGE_DASH_ON.toPx(), EDGE_DASH_OFF.toPx()) }
    val arrowStroke = with(density) { 2.dp.toPx() }
    // Room for the flags that hang under the tape. Most labels are wider than they are tall and
    // leave more than enough of it beside their own margin, and then nothing is taken off and the
    // tape sits in the middle of the canvas. Only a label tall enough to fill the canvas has to
    // give some height up for them.
    //
    // Worked out for every mode, not just the manual one where the flags are drawn, because a strip
    // that came and went with the mode would rescale and move the tape on every switch.
    val plain = if (boxSize.width > 0 && boxSize.height > 0) {
        min(boxSize.width / labelW, boxSize.height / labelH) * 0.96f
    } else 1f
    val bottomRoom = (flagHeight - (boxSize.height - labelH * plain) / 2f).coerceAtLeast(0f)
    val total = if (boxSize.width > 0 && boxSize.height > 0) {
        min(
            boxSize.width / labelW,
            (boxSize.height - bottomRoom).coerceAtLeast(1f) / labelH,
        ) * 0.96f
    } else 1f
    val contentTL = Offset(
        (boxSize.width - labelW * total) / 2f,
        (boxSize.height - bottomRoom - labelH * total) / 2f,
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
    val labelWState = rememberUpdatedState(labelW)
    val manualState = rememberUpdatedState(isManual)

    val background = Color(0xFF3A3A3A)
    val selectionColor = Color(0xFFE53935)
    val guideColor = Color(0xFF2979FF)
    val handleRadiusLabel = 18f

    val edgeColor = MaterialTheme.colorScheme.primary
    val edgeTextColor = MaterialTheme.colorScheme.onPrimary
    val startText = stringResource(R.string.edge_start)
    val endText = stringResource(R.string.edge_end)
    val flagPaint = remember(flagTextPx, edgeTextColor) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = flagTextPx
            color = edgeTextColor.toArgb()
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    // The arrow is drawn rather than typed: as a character it comes out at the size of the text,
    // which is far too small to read as a hint, and turns into a colour emoji on some devices.
    val arrowHalf = flagHeight * 0.26f
    val arrowGap = with(density) { EDGE_FLAG_ARROW_GAP.toPx() }
    val startTextW = remember(startText, flagPaint) { flagPaint.measureText(startText) }
    val endTextW = remember(endText, flagPaint) { flagPaint.measureText(endText) }
    val startFlagW = startTextW + arrowGap + 2f * arrowHalf + 2f * flagPad
    val endFlagW = endTextW + arrowGap + 2f * arrowHalf + 2f * flagPad

    // The two flags in screen coordinates, hanging flush under the tape: the left one grows to the
    // right of its line and the right one to the left of its, so both point inwards. Taken as
    // arguments rather than read from the state, so the drawing can pass its own values and the
    // gestures the live ones.
    fun edgeFlags(scale: Float, topLeft: Offset, widthPx: Float): Pair<Rect, Rect> {
        val top = topLeft.y + labelH * scale
        val rightX = topLeft.x + widthPx * scale
        return Rect(topLeft.x, top, topLeft.x + startFlagW, top + flagHeight) to
            Rect(rightX - endFlagW, top, rightX, top + flagHeight)
    }

    /** The edge a touch at [pos] (screen coordinates) means, or null for anything else. */
    fun edgeHandleAt(pos: Offset): LabelEdge? {
        if (!manualState.value) return null
        val scale = totalState.value
        val topLeft = tlState.value
        val width = labelWState.value
        val (leftFlag, rightFlag) = edgeFlags(scale, topLeft, width)
        // The flags are tested first: they are the roomy target and they overlap nothing. The band
        // around a line stays narrow, because every pixel of it lies over the tape and is one the
        // elements at that edge no longer get.
        fun onLine(x: Float): Boolean =
            abs(pos.x - x) < grabPx && pos.y > topLeft.y - grabPx && pos.y < leftFlag.top
        return when {
            leftFlag.contains(pos) -> LabelEdge.LEFT
            rightFlag.contains(pos) -> LabelEdge.RIGHT
            onLine(topLeft.x) -> LabelEdge.LEFT
            onLine(topLeft.x + width * scale) -> LabelEdge.RIGHT
            else -> null
        }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                var mode = 0 // 0 = nothing, 1 = move, 2 = scale, 3 = label edge
                detectDragGesturesWithDoubleTap(
                    onStart = { pos, snapFree ->
                        val edge = edgeHandleAt(pos)
                        val sc = totalState.value
                        val lp = (pos - tlState.value) / sc - Offset(offsetState.value, 0f)
                        val sel = elementsState.value.find { it.id == selectedIdState.value }
                        val onHandle = sel != null && run {
                            val b = elementBounds(sel)
                            (lp - Offset(b.right, b.bottom)).getDistance() < handleRadiusLabel
                        }
                        when {
                            // The label edges come first: they lie outside the tape, where there is
                            // no element that could be meant instead.
                            edge != null -> {
                                mode = 3
                                onEdgeDragStart(edge)
                            }
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
                            3 -> onEdgeDragBy(d.x)
                        }
                    },
                    onEnd = {
                        when (mode) {
                            1 -> onDragEnd()
                            2 -> onResizeEnd()
                            3 -> onEdgeDragEnd()
                        }
                        mode = 0
                        snapFreeDrag = false
                    },
                    onDoubleTap = { pos -> edgeHandleAt(pos)?.let(onEdgeFit) },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    // A tap on an edge handle belongs to the drag detector and must not clear the
                    // selection that happens to lie behind it.
                    if (edgeHandleAt(pos) != null) return@detectTapGestures
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

            drawSnapOverlay(
                guides, contentTL, labelW, labelH, total, offsetPx, isDieCut, cornerR, guideColor,
            )

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

            // The two edges of a manual length label: a dashed line down each side of the tape,
            // ending in a flag that hangs off its bottom corner and names it. The flag is the
            // comfortable grip, it points inwards, away from the screen edge where the back gesture
            // waits, and it is finger sized; the line is there for anyone who aims at the edge.
            if (isManual) {
                val (leftFlag, rightFlag) = edgeFlags(total, contentTL, labelW)
                val dashed = PathEffect.dashPathEffect(edgeDash)

                /** Double headed arrow around [cx], saying that this edge moves sideways. */
                fun arrow(cx: Float, cy: Float) {
                    val head = arrowHalf * 0.52f
                    drawLine(
                        color = edgeTextColor,
                        start = Offset(cx - arrowHalf, cy),
                        end = Offset(cx + arrowHalf, cy),
                        strokeWidth = arrowStroke,
                        cap = StrokeCap.Round,
                    )
                    listOf(-1f, 1f).forEach { side ->
                        val tip = Offset(cx + side * arrowHalf, cy)
                        listOf(-1f, 1f).forEach { updown ->
                            drawLine(
                                color = edgeTextColor,
                                start = tip,
                                end = Offset(tip.x - side * head, cy + updown * head),
                                strokeWidth = arrowStroke,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }

                listOf(leftFlag.left to leftFlag, rightFlag.right to rightFlag).forEach { (lineX, flag) ->
                    drawLine(
                        color = edgeColor,
                        start = Offset(lineX, contentTL.y),
                        end = Offset(lineX, flag.top),
                        strokeWidth = edgeLineWidth,
                        pathEffect = dashed,
                    )
                    drawRoundRect(
                        color = edgeColor,
                        topLeft = flag.topLeft,
                        size = flag.size,
                        cornerRadius = CornerRadius(5f, 5f),
                    )
                }
                // Word first on the leading edge, arrow first on the trailing one, so the arrow of
                // each flag sits on the side the flag points to.
                val baseline = leftFlag.center.y - (flagPaint.descent() + flagPaint.ascent()) / 2f
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(startText, leftFlag.left + flagPad, baseline, flagPaint)
                    c.nativeCanvas.drawText(
                        endText,
                        rightFlag.left + flagPad + 2f * arrowHalf + arrowGap,
                        baseline,
                        flagPaint,
                    )
                }
                arrow(leftFlag.right - flagPad - arrowHalf, leftFlag.center.y)
                arrow(rightFlag.left + flagPad + arrowHalf, rightFlag.center.y)
            }
        }
    }
}

/** How small a reference may get on screen before its box would be unreadable. Device px. */
private const val MIN_REF_PX = 12f

/**
 * Shows what the dragged element is currently aligned to: a dashed line across the label at the
 * snapped coordinate, a thin dashed box around the reference, and a solid bar on the anchor of that
 * reference the element locked onto. The reference is either another element or the label itself,
 * and then the label's own frame is the box, which is what tells a label center apart from an
 * element center.
 */
private fun DrawScope.drawSnapOverlay(
    guides: SnapGuides,
    contentTL: Offset,
    labelW: Float,
    labelH: Float,
    total: Float,
    offsetPx: Float,
    isDieCut: Boolean,
    cornerR: Float,
    color: Color,
) {
    val x = guides.x
    val y = guides.y
    if (x == null && y == null) return

    // Frame coordinates for the label's own lines, element coordinates for everything a guide or a
    // reference box carries, because the anchored length modes shift the two against each other.
    fun toScreen(lx: Float, ly: Float) = contentTL + Offset(lx * total, ly * total)
    fun elToScreen(lx: Float, ly: Float) = toScreen(lx + offsetPx, ly)
    val refDash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    val lineDash = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
    val refStroke = Stroke(width = 1.5f, pathEffect = refDash)

    // One box per reference, so an element both axes snapped to is outlined once, not twice.
    listOfNotNull(x, y).map { it.refBounds }.distinct().forEach { ref ->
        if (ref == null) {
            // The label is the reference, so its own frame is the box, rounded on a die-cut label.
            val size = Size(labelW * total, labelH * total)
            if (isDieCut) {
                drawRoundRect(
                    color, topLeft = contentTL, size = size,
                    cornerRadius = CornerRadius(cornerR * total, cornerR * total),
                    style = refStroke,
                )
            } else {
                drawRect(color, topLeft = contentTL, size = size, style = refStroke)
            }
        } else {
            // Grown about its center when the element is a hairline, so the box does not collapse
            // into nothing. Only the box is grown; the line and the bar keep the true coordinates.
            val w = max(ref.width * total, MIN_REF_PX)
            val h = max(ref.height * total, MIN_REF_PX)
            val c = elToScreen(ref.center.x, ref.center.y)
            drawRect(color, topLeft = c - Offset(w / 2f, h / 2f), size = Size(w, h), style = refStroke)
        }
    }

    x?.let { drawLine(color, elToScreen(it.line, 0f), elToScreen(it.line, labelH), strokeWidth = 2f, pathEffect = lineDash) }
    y?.let { drawLine(color, toScreen(0f, it.line), toScreen(labelW, it.line), strokeWidth = 2f, pathEffect = lineDash) }

    // The bars go last, so they read on top of both the box and the line. A label anchor is nudged
    // half a stroke inwards, because a bar centered on the tape edge would sit half in the gray
    // border; an element anchor never is, it has to stay exactly on the edge it names.
    fun nudge(g: SnapGuide) = if (g.refBounds != null) 0f else when (g.anchor) {
        SnapAnchor.LEADING -> 2.5f / total
        SnapAnchor.TRAILING -> -2.5f / total
        SnapAnchor.CENTER -> 0f
    }
    val inset = if (isDieCut) cornerR else 0f

    x?.let { g ->
        val lx = g.line + nudge(g)
        val ref = g.refBounds
        if (ref == null) {
            drawLine(color, toScreen(lx, inset), toScreen(lx, labelH - inset), strokeWidth = 5f)
        } else {
            val half = max(ref.height * total, MIN_REF_PX) / 2f
            val c = elToScreen(lx, ref.center.y)
            drawLine(color, c - Offset(0f, half), c + Offset(0f, half), strokeWidth = 5f)
        }
    }
    y?.let { g ->
        val ly = g.line + nudge(g)
        val ref = g.refBounds
        if (ref == null) {
            drawLine(color, toScreen(inset, ly), toScreen(labelW - inset, ly), strokeWidth = 5f)
        } else {
            val half = max(ref.width * total, MIN_REF_PX) / 2f
            val c = elToScreen(ref.center.x, ly)
            drawLine(color, c - Offset(half, 0f), c + Offset(half, 0f), strokeWidth = 5f)
        }
    }
}

/**
 * Drag detector that also recognises "double tap, then drag": when the finger goes down again
 * within the double tap timeout, the drag is reported with snapFree = true. The tap itself stays
 * with the separate tap detector, which selects the element, so this one only has to notice that
 * a second down followed. [onDrag] receives the raw screen delta, like detectDragGestures does.
 *
 * A double tap that goes nowhere is reported through [onDoubleTap] instead. That is done here and
 * not with an onDoubleTap on the tap detector, because that one would hold back every single tap
 * in the editor for the length of the double tap timeout.
 */
private suspend fun PointerInputScope.detectDragGesturesWithDoubleTap(
    onStart: (Offset, Boolean) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
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
    // A plain double tap without any movement: nothing to drag, but the spot it happened on is
    // passed on, so a double tap on one of the label edge handles can act on it.
    val dragStart = slop ?: run {
        if (snapFree) onDoubleTap(start.position)
        return@awaitEachGesture
    }

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
