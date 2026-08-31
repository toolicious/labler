package io.github.toolicious.labler.ui.editor

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.toolicious.labler.App
import io.github.toolicious.labler.model.BarcodeElement
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.IconElement
import io.github.toolicious.labler.model.ImageElement
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.LengthMode
import io.github.toolicious.labler.model.Symbology
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.Protocol
import io.github.toolicious.labler.render.LabelRenderer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The two ends of a manual length label, as they are grabbed in the editor. LEFT is the leading
 * edge (the blank tape in front of the content), RIGHT is the end of the label.
 */
enum class LabelEdge { LEFT, RIGHT }

/** Blank tape an edge keeps to the content when it is fitted to it (double tap on its handle). */
private const val FIT_GAP_MM = 1

class EditorViewModel(app: Application, private val templateId: String) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val repo = container.templateRepository

    private val _template = MutableStateFlow<LabelTemplate?>(null)
    val template = _template.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId = _selectedId.asStateFlow()

    val selectedElement = combine(_template, _selectedId) { t, id ->
        t?.elements?.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _guides = MutableStateFlow(SnapGuides())
    val guides = _guides.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    init {
        viewModelScope.launch { _template.value = repo.get(templateId) }
    }

    fun select(id: String?) {
        _selectedId.value = id
    }

    // ----- Mutations with undo history -----

    private val undoStack = ArrayDeque<List<LabelElement>>()
    private val redoStack = ArrayDeque<List<LabelElement>>()
    private var lastHistoryKey: String? = null
    private var lastHistoryTime = 0L

    /**
     * History snapshot before a mutation. Consecutive edits on the
     * same element (e.g. typing in a text field) are coalesced.
     */
    private fun pushHistory(coalesceKey: String?) {
        val current = _template.value?.elements ?: return
        val now = System.currentTimeMillis()
        val coalesce = coalesceKey != null &&
            coalesceKey == lastHistoryKey &&
            now - lastHistoryTime < 800
        lastHistoryKey = coalesceKey
        lastHistoryTime = now
        if (coalesce) return
        undoStack.addLast(current)
        while (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        val t = _template.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(t.elements)
        lastHistoryKey = null
        applyElements(previous)
        updateHistoryFlags()
    }

    fun redo() {
        val t = _template.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(t.elements)
        lastHistoryKey = null
        applyElements(next)
        updateHistoryFlags()
    }

    private fun applyElements(elements: List<LabelElement>) {
        val t = _template.value ?: return
        val updated = t.copy(elements = elements)
        _template.value = updated
        if (_selectedId.value != null && elements.none { it.id == _selectedId.value }) {
            _selectedId.value = null
        }
        scheduleSave(updated)
    }

    private fun mutate(coalesceKey: String?, transform: (LabelTemplate) -> LabelTemplate) {
        val current = _template.value ?: return
        pushHistory(coalesceKey)
        val updated = transform(current)
        _template.value = updated
        scheduleSave(updated)
    }

    /**
     * The tape as the elements see it. Element coordinates and the label frame only line up on a
     * fixed label: the other two modes draw the content shifted, so anything that has to match what
     * is on screen goes through here rather than assuming the tape starts at zero.
     */
    private fun tapeRect(t: LabelTemplate): Rect {
        val left = -LabelRenderer.contentOffsetPx(t.spec, t.elements)
        return Rect(
            left,
            0f,
            left + LabelRenderer.effectiveLengthPx(t.spec, t.elements),
            LabelSpec.PRINT_HEIGHT_PX.toFloat(),
        )
    }

    /**
     * Where the tape begins. It is not the origin of the element coordinates: a manual label starts
     * them inside the tape and a variable one wherever its content happens to be, so something laid
     * out from zero would sit as far off the tape as the layout is shifted, which after a few drags
     * is well out of sight.
     */
    val tapeStartPx: Float get() = _template.value?.let { tapeRect(it).left } ?: 0f

    /**
     * Where a new element goes: flush against the right edge of the selected one and centered on
     * it, so adding several in a row lines them up, and in the middle of the tape when nothing is
     * selected. Both beat a fixed corner, which after any amount of editing is nowhere in
     * particular and, on the two shifted layouts, not even on the tape.
     */
    private fun placed(t: LabelTemplate, element: LabelElement): LabelElement {
        // Twice, because on a variable label the tape is partly made by the element being placed:
        // the first round gives the tape without it, the second the tape it will be on.
        var out = element
        repeat(2) { out = placedOnce(t, out) }
        return out
    }

    private fun placedOnce(t: LabelTemplate, element: LabelElement): LabelElement {
        val size = LabelRenderer.measure(element)
        val tape = tapeRect(t.copy(elements = t.elements + element))
        val beside = t.elements.find { it.id == _selectedId.value }?.let { elementBounds(it) }
        val x = if (beside != null) beside.right else tape.left + (tape.width - size.width) / 2f
        val y = if (beside != null) beside.center.y - size.height / 2f
        else (tape.height - size.height) / 2f

        // A variable label grows to hold whatever is put on it. The other two have edges to stay
        // within, and the same 8 px of a new element stays on the tape as of a dragged one.
        val onTapeX =
            if (t.spec.lengthIsAuto) x else x.coerceIn(tape.left + 8f - size.width, tape.right - 8f)
        // An element taller than the tape is centered on it and hangs over both ways; one that fits
        // is kept fully on it, however high or low the element it was placed beside sits.
        val room = tape.height - size.height
        val onTapeY = if (room < 0f) room / 2f else y.coerceIn(0f, room)
        return element.moved(onTapeX - element.x, onTapeY - element.y)
    }

    /**
     * @param place false keeps the coordinates the element was built with, for the ones that are
     *   worked out from what they wrap or span rather than simply put somewhere free.
     */
    fun addElement(element: LabelElement, place: Boolean = true) {
        mutate(null) { t ->
            t.copy(elements = t.elements + if (place) placed(t, element) else element)
        }
        _selectedId.value = element.id
    }

    fun updateElement(element: LabelElement) = mutate(element.id) { t ->
        val adjusted = reanchorOnEdit(t, element)
        t.copy(elements = t.elements.map { if (it.id == adjusted.id) adjusted else it })
    }

    /**
     * Keeps the anchor (center or right edge) fixed for centered/right-aligned text
     * when the text width changes due to input, size, font, or style.
     */
    private fun reanchorOnEdit(t: LabelTemplate, incoming: LabelElement): LabelElement {
        if (incoming !is TextElement) return incoming
        val old = t.elements.find { it.id == incoming.id } as? TextElement ?: return incoming
        val sameMetrics = old.text == incoming.text && old.fontSizePx == incoming.fontSizePx &&
            old.bold == incoming.bold && old.italic == incoming.italic && old.font == incoming.font
        return if (sameMetrics) incoming else anchorText(old, incoming)
    }

    /**
     * Keeps the anchor fixed when a text element is resized: horizontally depending on
     * alignment (left/center/right), vertically always the center.
     */
    private fun anchorText(old: TextElement, updated: TextElement): TextElement {
        val s0 = LabelRenderer.measure(old)
        val s1 = LabelRenderer.measure(updated)
        val dx = when (updated.align) {
            LabelTextAlign.CENTER -> -(s1.width - s0.width) / 2f
            LabelTextAlign.RIGHT -> -(s1.width - s0.width)
            LabelTextAlign.LEFT -> 0f
        }
        val dy = -(s1.height - s0.height) / 2f
        return updated.copy(x = updated.x + dx, y = updated.y + dy)
    }

    fun deleteSelected() {
        val sel = _selectedId.value ?: return
        mutate(null) { t -> t.copy(elements = t.elements.filterNot { it.id == sel }) }
        _selectedId.value = null
    }

    fun updateSpec(spec: LabelSpec) = mutate(null) { it.copy(spec = spec) }

    /** Updates name and dimensions together (from the editor title), applied immediately. */
    fun updateMeta(name: String, spec: LabelSpec) =
        mutate(null) {
            it.copy(
                name = name.ifBlank { it.name },
                spec = spec,
                elements = LabelRenderer.rebasedForMode(it.spec, it.elements, spec),
            )
        }

    fun moveSelected(dx: Float, dy: Float) {
        val element = selectedElement.value ?: return
        updateElement(element.moved(dx, dy))
    }

    // ----- Drag with snapping (by element id, so the selection does not lag behind) -----

    private var dragId: String? = null
    private var dragRaw: Offset? = null
    // Set for a drag started out of a double tap: that one moves freely, without snapping.
    private var dragSnapFree = false
    // Snap lines from the other elements, cached at drag start (they do not move during the drag).
    private var dragXTargets: List<SnapTarget> = emptyList()
    // Both worked out once per drag: they depend on the other elements and on the size of the
    // dragged one, and neither changes while it is being moved.
    private var dragAutoCenter: Float? = null
    private var dragOthersLeft = Float.POSITIVE_INFINITY
    private var dragYTargets: List<SnapTarget> = emptyList()

    fun beginDrag(id: String, snapFree: Boolean) {
        val t = _template.value ?: return
        val el = t.elements.find { it.id == id } ?: return
        _selectedId.value = id
        pushHistory(null)
        dragId = id
        dragRaw = Offset(el.x, el.y)
        dragSnapFree = snapFree
        if (snapFree) {
            dragXTargets = emptyList()
            dragYTargets = emptyList()
            dragAutoCenter = null
            dragOthersLeft = Float.POSITIVE_INFINITY
            _guides.value = SnapGuides()
            return
        }
        dragAutoCenter = autoCenterPx(t, el)
        dragOthersLeft = t.elements
            .filter { it.id != id }
            .minOfOrNull { elementBounds(it).left } ?: Float.POSITIVE_INFINITY
        // Each line comes from the rotated box, the same one the canvas outlines, and the box is
        // carried along with it so the canvas can highlight exactly the geometry a snap used.
        //
        // Only what is on the tape offers lines. Pulling the edges of a manual label in past an
        // element leaves it outside, where it is not printed and cannot be seen, and a guide from
        // it would point at nothing.
        val tape = tapeRect(t)
        val (xt, yt) = elementTargets(
            t.elements
                .filter { it.id != id }
                .map { elementBounds(it) }
                .filter { it.overlaps(tape) }
        )
        dragXTargets = xt
        dragYTargets = yt
    }

    fun dragBy(delta: Offset) {
        val id = dragId ?: return
        val spec = _template.value?.spec ?: return
        val el = _template.value?.elements?.find { it.id == id } ?: return
        val raw = (dragRaw ?: Offset(el.x, el.y)) + delta
        dragRaw = raw

        // Snapping and the placement bound both work on the rotated box the canvas draws, not on
        // the unrotated one, so a guide lands on an edge the user can see and the element stays
        // grabbable by the shape it is grabbed by. The offset from the element origin to that box
        // depends only on size and rotation, so it holds for the whole drag. Both are zero for an
        // unrotated element, which leaves the common case exactly as it was.
        val box = elementBounds(el)
        val offX = box.left - el.x
        val offY = box.top - el.y

        var nx = raw.x
        var ny = raw.y
        var xGuide: SnapGuide? = null
        var yGuide: SnapGuide? = null

        // In element coordinates the tape starts wherever the drawing puts it: at zero for a fixed
        // label, and at minus the leading edge for a manual one.
        val labelStart = (-spec.leadingPx).toFloat()

        if (!dragSnapFree) {
            // The label's own lines first, then the cached lines of the other elements, so a line
            // they share is credited to the label.
            val snapX =
                bestSnapAxis(raw.x + offX, box.width, labelXTargets(spec, dragAutoCenter) + dragXTargets)
            val snapY = bestSnapAxis(raw.y + offY, box.height, labelYTargets() + dragYTargets)
            nx += snapX?.shift ?: 0f
            ny += snapY?.shift ?: 0f
            xGuide = snapX?.guide
            yGuide = snapY?.guide
        }

        // Keep at least 8 px grabbable. This is placement, not snapping, so it always applies.
        // A variable label grows rather than stopping anything, so the only bound to the right is
        // the longest label the printer and the UI accept. To the left it depends on company: with
        // others on the tape, going past the front pushes them along and the label grows, so there
        // is something to see; on its own an element would only run its coordinates out under a
        // picture that has already stopped, and it is held at the margin instead.
        val bound = LabelSpec.MAX_LENGTH_PX.toFloat()
        val minX = when {
            !spec.lengthIsAuto -> labelStart + 8f - box.width
            dragOthersLeft.isFinite() -> -bound
            else -> LabelRenderer.AUTO_LENGTH_MARGIN_PX
        }
        val maxX = if (spec.lengthIsAuto) bound else labelStart + spec.lengthPx
        val boxX = (nx + offX).coerceIn(minX, maxX - 8f)
        val boxY = (ny + offY).coerceIn(8f - box.height, LabelSpec.PRINT_HEIGHT_PX - 8f)
        // A guide would point at a line the element is no longer on once the bound had to move it.
        if (boxX != nx + offX) {
            nx = boxX - offX
            xGuide = null
        }
        if (boxY != ny + offY) {
            ny = boxY - offY
            yGuide = null
        }

        if (!dragSnapFree) _guides.value = SnapGuides(xGuide, yGuide)
        applyWithoutHistory(el.moved(nx - el.x, ny - el.y))
    }

    /**
     * The center of a variable-length label, or null when it would not stay put for this drag.
     *
     * Such a label is laid out from its content, so the element being dragged can be the one
     * deciding where the tape begins and how long it is. Its center is the one line that can
     * survive that: an element parked in the middle usually decides neither, and even one wide
     * enough to stick out at both ends ends up centered on the label it made.
     *
     * Rather than work out which of those cases applies, the center is simply checked: it is taken
     * from the other elements, the dragged one is put on it, and it is taken again. Only a center
     * that has not moved is offered, so a line is never shown that the snap itself would undo.
     */
    private fun autoCenterPx(t: LabelTemplate, dragged: LabelElement): Float? {
        if (!t.spec.lengthIsAuto) return null
        val others = t.elements.filter { it.id != dragged.id }

        fun center(elements: List<LabelElement>): Float =
            -LabelRenderer.contentOffsetPx(t.spec, elements) +
                LabelRenderer.effectiveLengthPx(t.spec, elements) / 2f

        // Starts from the frame the label really has and settles from there: an element that
        // currently sticks out is holding the frame open, so the first answer is still the old one
        // and only the next round gives the frame it would leave behind.
        var candidate = center(t.elements)
        repeat(3) {
            val centered = dragged.moved(candidate - elementBounds(dragged).center.x, 0f)
            val next = center(others + centered)
            if (abs(next - candidate) < 0.5f) return candidate
            candidate = next
        }
        return null
    }

    fun endDrag() {
        dragId = null
        dragAutoCenter = null
        dragRaw = null
        dragSnapFree = false
        dragXTargets = emptyList()
        dragYTargets = emptyList()
        _guides.value = SnapGuides()
    }

    private var resizeId: String? = null

    fun beginResize(id: String) {
        _template.value?.elements?.find { it.id == id } ?: return
        _selectedId.value = id
        pushHistory(null)
        resizeId = id
    }

    fun endResize() {
        resizeId = null
    }

    fun resizeSelectedBy(delta: Offset) {
        val id = resizeId ?: return
        val el = _template.value?.elements?.find { it.id == id } ?: return
        val updated = when (el) {
            is TextElement -> {
                val size = LabelRenderer.measure(el)
                val factor = ((size.width + delta.x) / size.width).coerceIn(0.8f, 1.25f)
                val scaled = el.copy(
                    fontSizePx = (el.fontSizePx * factor).coerceIn(8f, 96f),
                    boxWidthPx = el.boxWidthPx?.let { (it * factor).coerceAtLeast(16f) }
                )
                anchorText(el, scaled)
            }
            is IconElement -> el.copy(
                sizePx = (el.sizePx + max(delta.x, delta.y)).coerceIn(16f, 96f)
            )
            is FrameElement -> el.copy(
                widthPx = (el.widthPx + delta.x).coerceAtLeast(8f),
                heightPx = (el.heightPx + delta.y).coerceIn(8f, LabelSpec.PRINT_HEIGHT_PX.toFloat())
            )
            is BarcodeElement -> if (el.symbology == Symbology.QR_CODE) {
                // QR stays square.
                val s = (minOf(el.widthPx, el.heightPx) + max(delta.x, delta.y))
                    .coerceIn(24f, LabelSpec.PRINT_HEIGHT_PX.toFloat())
                el.copy(widthPx = s, heightPx = s)
            } else {
                el.copy(
                    widthPx = (el.widthPx + delta.x).coerceAtLeast(32f),
                    heightPx = (el.heightPx + delta.y).coerceIn(16f, LabelSpec.PRINT_HEIGHT_PX.toFloat())
                )
            }
            is ImageElement -> el.copy(
                // Width scales, height follows via the aspect ratio.
                widthPx = (el.widthPx + max(delta.x, delta.y)).coerceIn(16f, 480f)
            )
        }
        applyWithoutHistory(updated)
    }

    private fun applyWithoutHistory(element: LabelElement) {
        val t = _template.value ?: return
        val updated = t.copy(elements = t.elements.map { if (it.id == element.id) element else it })
        _template.value = updated
        scheduleSave(updated)
    }

    // ----- Label edges (manual length only) -----

    private var edgeDrag: LabelEdge? = null
    // Raw movement since the drag started, in label px. The spec only knows whole millimetres, so
    // the fractional remainder has to live here instead of being rounded away every frame.
    private var edgeRawPx = 0f
    private var edgeStartLeadingMm = 0
    private var edgeStartLengthMm = 0

    /**
     * Where the content starts and ends on the label, in whole millimetres, rounded outwards so a
     * rounding step can never cut anything off. Uses the same bounding box the canvas draws around
     * a selected element, which is where the rotation of an element is accounted for. Null on an
     * empty label, which has nothing to measure against.
     */
    private fun contentBoundsMm(spec: LabelSpec, elements: List<LabelElement>): Pair<Int, Int>? {
        if (elements.isEmpty()) return null
        val left = elements.minOf { elementBounds(it).left } + spec.leadingPx
        val right = elements.maxOf { elementBounds(it).right } + spec.leadingPx
        return floor(left / Protocol.DOTS_PER_MM).toInt() to ceil(right / Protocol.DOTS_PER_MM).toInt()
    }

    /**
     * Moves the leading edge to [mm]. The elements keep their coordinates, so the length carries
     * the same change and the content stays where it is on the tape.
     */
    fun setLeadingMm(mm: Int) {
        val t = _template.value ?: return
        applyEdges(withLeadingMm(t.spec, mm), history = true)
    }

    /** Moves the trailing edge, which is the length of the label. */
    fun setLengthMm(mm: Int) {
        val t = _template.value ?: return
        applyEdges(withLengthMm(t.spec, mm), history = true)
    }

    /** Grabs one of the two edges. Only a manual length label has edges to grab. */
    fun beginEdgeDrag(edge: LabelEdge) {
        val t = _template.value ?: return
        if (t.spec.lengthMode != LengthMode.MANUAL) return
        pushHistory(null)
        edgeDrag = edge
        edgeRawPx = 0f
        edgeStartLeadingMm = t.spec.leadingMm
        edgeStartLengthMm = t.spec.lengthMm
    }

    /**
     * Moves the grabbed edge by [dxPx] label pixels. The result is always computed from the values
     * the drag started with, so the rounding to whole millimetres cannot drift over a long drag.
     */
    fun dragEdgeBy(dxPx: Float) {
        val edge = edgeDrag ?: return
        val t = _template.value ?: return
        edgeRawPx += dxPx
        val mm = (edgeRawPx / Protocol.DOTS_PER_MM).roundToInt()
        val spec = when (edge) {
            // Moving the leading edge to the left (negative) grows the label in front of the content.
            LabelEdge.LEFT -> withLeadingMm(t.spec, edgeStartLeadingMm - mm)
            LabelEdge.RIGHT -> withLengthMm(t.spec, edgeStartLengthMm + mm)
        }
        applyEdges(spec, history = false)
    }

    fun endEdgeDrag() {
        edgeDrag = null
        edgeRawPx = 0f
    }

    /** Double tap on an edge: pulls it up to [FIT_GAP_MM] from the content. */
    fun fitEdge(edge: LabelEdge) {
        val t = _template.value ?: return
        val (left, right) = contentBoundsMm(t.spec, t.elements) ?: return
        when (edge) {
            LabelEdge.LEFT -> setLeadingMm(t.spec.leadingMm + FIT_GAP_MM - left)
            LabelEdge.RIGHT -> setLengthMm(right + FIT_GAP_MM)
        }
    }

    /**
     * [spec] with the leading edge at [mm]. As much as the length clamp allows is what the edge
     * really moves, so the gap in front of the content matches the length change in every case.
     *
     * [mm] may be negative, which puts the leading edge inside the content and prints it cut off
     * at the front, the same way the trailing edge cuts it off at the back. What stops the edge is
     * the shortest label the printer takes, not the content.
     */
    private fun withLeadingMm(spec: LabelSpec, mm: Int): LabelSpec {
        val lengthMm = (spec.lengthMm + (mm - spec.leadingMm))
            .coerceIn(LabelSpec.MIN_LENGTH_MM, LabelSpec.MAX_LENGTH_MM)
        return spec.copy(
            leadingMm = spec.leadingMm + (lengthMm - spec.lengthMm),
            lengthMm = lengthMm,
        )
    }

    /**
     * [spec] with the trailing edge at [mm]. The edge is free to move in past the content, which
     * then prints cut off at the label edge. Nothing is lost by that: the elements keep their
     * coordinates and come back into view as soon as the edge is pulled out again.
     */
    private fun withLengthMm(spec: LabelSpec, mm: Int): LabelSpec = spec.copy(
        lengthMm = mm.coerceIn(LabelSpec.MIN_LENGTH_MM, LabelSpec.MAX_LENGTH_MM),
    )

    /**
     * Writes an edge change back. [history] is off while dragging, which takes its one snapshot at
     * the start of the drag instead of on every frame. The single write path of the whole section,
     * so this is where the other two length modes are kept out: a variable label derives its length
     * from the content and a fixed one gets it typed in, neither has edges to move.
     */
    private fun applyEdges(spec: LabelSpec, history: Boolean) {
        val t = _template.value ?: return
        if (t.spec.lengthMode != LengthMode.MANUAL || spec == t.spec) return
        if (history) {
            updateSpec(spec)
        } else {
            val updated = t.copy(spec = spec)
            _template.value = updated
            scheduleSave(updated)
        }
    }

    // ----- Persistence -----

    private var saveJob: Job? = null

    /** Debounced in the app scope, so switching screens does not lose a save. */
    private fun scheduleSave(template: LabelTemplate) {
        saveJob?.cancel()
        saveJob = container.applicationScope.launch {
            delay(500)
            repo.save(template)
        }
    }

    companion object {
        fun factory(templateId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                EditorViewModel(app, templateId)
            }
        }
    }
}
