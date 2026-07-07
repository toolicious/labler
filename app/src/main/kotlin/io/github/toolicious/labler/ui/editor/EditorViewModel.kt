package io.github.toolicious.labler.ui.editor

import android.app.Application
import androidx.compose.ui.geometry.Offset
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
import io.github.toolicious.labler.model.Symbology
import io.github.toolicious.labler.model.TextElement
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
import kotlin.math.max

/** Active snap guide lines during dragging, as label coordinates (null = no guide on that axis). */
data class SnapGuides(val xLine: Float? = null, val yLine: Float? = null)

private const val LABEL_SNAP_TOL = 4f // label center and borders
private const val ELEM_SNAP_TOL = 3f  // small zone for aligning to other elements

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

    fun addElement(element: LabelElement) {
        mutate(null) { it.copy(elements = it.elements + element) }
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
        mutate(null) { it.copy(name = name.ifBlank { it.name }, spec = spec) }

    fun moveSelected(dx: Float, dy: Float) {
        val element = selectedElement.value ?: return
        updateElement(element.moved(dx, dy))
    }

    // ----- Drag with snapping (by element id, so the selection does not lag behind) -----

    private var dragId: String? = null
    private var dragRaw: Offset? = null
    // Snap lines from the other elements, cached at drag start (they do not move during the drag).
    private var dragXTargets: List<SnapTarget> = emptyList()
    private var dragYTargets: List<SnapTarget> = emptyList()

    fun beginDrag(id: String) {
        val t = _template.value ?: return
        val el = t.elements.find { it.id == id } ?: return
        _selectedId.value = id
        pushHistory(null)
        dragId = id
        dragRaw = Offset(el.x, el.y)
        val xt = mutableListOf<SnapTarget>()
        val yt = mutableListOf<SnapTarget>()
        t.elements.forEach { other ->
            if (other.id == id) return@forEach
            val os = LabelRenderer.measure(other)
            xt += SnapTarget(other.x, true, ELEM_SNAP_TOL)
            xt += SnapTarget(other.x + os.width / 2f, true, ELEM_SNAP_TOL)
            xt += SnapTarget(other.x + os.width, true, ELEM_SNAP_TOL)
            yt += SnapTarget(other.y, true, ELEM_SNAP_TOL)
            yt += SnapTarget(other.y + os.height / 2f, true, ELEM_SNAP_TOL)
            yt += SnapTarget(other.y + os.height, true, ELEM_SNAP_TOL)
        }
        dragXTargets = xt
        dragYTargets = yt
    }

    fun dragBy(delta: Offset) {
        val id = dragId ?: return
        val spec = _template.value?.spec ?: return
        val el = _template.value?.elements?.find { it.id == id } ?: return
        val raw = (dragRaw ?: Offset(el.x, el.y)) + delta
        dragRaw = raw

        val size = LabelRenderer.measure(el)
        val centerX = spec.lengthPx / 2f
        val centerY = LabelSpec.PRINT_HEIGHT_PX / 2f

        // Label center + borders, then the cached lines of the other elements.
        val xTargets = listOf(
            SnapTarget(centerX, true, LABEL_SNAP_TOL),
            SnapTarget(0f, false, LABEL_SNAP_TOL),
            SnapTarget(spec.lengthPx.toFloat(), false, LABEL_SNAP_TOL),
        ) + dragXTargets
        val yTargets = listOf(
            SnapTarget(centerY, true, LABEL_SNAP_TOL),
            SnapTarget(0f, false, LABEL_SNAP_TOL),
            SnapTarget(LabelSpec.PRINT_HEIGHT_PX.toFloat(), false, LABEL_SNAP_TOL),
        ) + dragYTargets

        val snapX = bestSnapAxis(raw.x, size.width, xTargets)
        val snapY = bestSnapAxis(raw.y, size.height, yTargets)

        var nx = snapX?.origin ?: raw.x
        var ny = snapY?.origin ?: raw.y

        // Keep at least 8 px grabbable
        nx = nx.coerceIn(8f - size.width, spec.lengthPx - 8f)
        ny = ny.coerceIn(8f - size.height, LabelSpec.PRINT_HEIGHT_PX - 8f)

        _guides.value = SnapGuides(snapX?.guideLine, snapY?.guideLine)
        applyWithoutHistory(el.moved(nx - el.x, ny - el.y))
    }

    private data class SnapTarget(val line: Float, val guide: Boolean, val tol: Float)
    private data class AxisSnap(val origin: Float, val guideLine: Float?)

    /**
     * Picks the closest snap target on an axis. The dragged element snaps by its leading edge,
     * center, or trailing edge to any target line within that target's tolerance. Returns the new
     * origin and, when the target should show a guide, its label coordinate.
     */
    private fun bestSnapAxis(origin: Float, extent: Float, targets: List<SnapTarget>): AxisSnap? {
        val features = floatArrayOf(origin, origin + extent / 2f, origin + extent)
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
        return AxisSnap(origin = target.line - (bestFeature - origin), guideLine = if (target.guide) target.line else null)
    }

    fun endDrag() {
        dragId = null
        dragRaw = null
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
