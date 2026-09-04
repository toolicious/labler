package io.github.toolicious.labler.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.OverviewPrefs
import io.github.toolicious.labler.data.TemplateSort
import io.github.toolicious.labler.data.TemplateViewMode
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.model.LabelTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Last known layout and order, held for the length of the process and preloaded by
 * [io.github.toolicious.labler.App], so the overview does not come up as a grid in the wrong order
 * for one frame before the stored settings arrive.
 */
internal var lastOverviewPrefs = OverviewPrefs()

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val repo = container.templateRepository

    val printerState = container.printerManager.state
    val savedPrinter = container.settings.savedPrinter
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _filter = MutableStateFlow(TemplateFilter())
    val filter = _filter.asStateFlow()

    val prefs = container.settings.overviewPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, lastOverviewPrefs)

    /**
     * Every stored label with the length it actually reaches. Measured once per change of the
     * list and off the main thread, because a variable label has to be laid out to be measured,
     * and shared so that the facets and the list itself do not each measure their own.
     */
    private val measured = repo.observeAll()
        .map { list ->
            list.map { MeasuredTemplate(it, LabelRenderer.effectiveLengthMm(it.spec, it.elements)) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Which chips to offer and how many labels are behind each. Built from the searched list and
     * not from the filtered one, so neither the chips nor their numbers move around while filters
     * are being set.
     */
    val facets = combine(measured, _query) { items, q -> facetsOf(items.searched(q)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateFacets())

    val templates = combine(measured, _query, prefs, _filter) { items, q, p, f ->
        items.searched(q)
            .filter { f.matches(it) }
            .map { it.template }
            .sortedWith(templateComparator(p.sort, p.ascending))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setViewMode(mode: TemplateViewMode) = savePrefs(prefs.value.copy(viewMode = mode))

    /**
     * Picking the criterion that is already active turns it around; picking another switches to it
     * the way round it reads best. One entry per criterion, never two.
     */
    fun selectSort(sort: TemplateSort) {
        val current = prefs.value
        savePrefs(
            if (current.sort == sort) current.copy(ascending = !current.ascending)
            else current.copy(sort = sort, ascending = sort.ascendingByDefault)
        )
    }

    /** Back to the order the overview starts out with, for the long press on the sort button. */
    fun resetSort() = savePrefs(
        prefs.value.copy(
            sort = TemplateSort.DEFAULT,
            ascending = TemplateSort.DEFAULT.ascendingByDefault,
        )
    )

    private fun savePrefs(next: OverviewPrefs) {
        lastOverviewPrefs = next
        viewModelScope.launch { container.settings.saveOverviewPrefs(next) }
    }

    fun setFilter(value: TemplateFilter) {
        _filter.value = value
    }

    fun clearFilter() {
        _filter.value = TemplateFilter()
    }

    fun create(name: String, spec: LabelSpec, defaultName: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val template = repo.create(name, spec, defaultName)
            onCreated(template.id)
        }
    }

    fun duplicate(id: String, newName: String) {
        viewModelScope.launch { repo.duplicate(id, newName) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }

    /** Updates the name and dimensions of an existing template (elements are kept). */
    fun updateMeta(id: String, name: String, spec: LabelSpec) {
        viewModelScope.launch {
            val current = repo.get(id) ?: return@launch
            repo.save(
                current.copy(
                    name = name.ifBlank { current.name },
                    spec = spec,
                    elements = LabelRenderer.rebasedForMode(current.spec, current.elements, spec),
                )
            )
        }
    }

    /** Active connection attempt to the remembered printer (tap on the status chip). */
    fun connectSaved() = container.printerManager.connectSavedActive()

    fun toggleFavorite(template: LabelTemplate) {
        viewModelScope.launch { repo.setFavorite(template.id, !template.favorite) }
    }

    /** Writes the template as JSON to the chosen SAF Uri. */
    fun exportTo(uri: Uri, template: LabelTemplate, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            // Determine success/failure from the result, not from the (possibly null) exception message.
            val error = runCatching {
                val payload = container.templateJson.encode(template)
                app.contentResolver.openOutputStream(uri)
                    ?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    ?: error("output stream unavailable")
            }.fold(onSuccess = { null }, onFailure = { app.getString(R.string.err_file_not_writable) })
            withContext(Dispatchers.Main) { onResult(error) }
        }
    }

    /** Reads an exported template and creates it as a new template. */
    fun importFrom(uri: Uri, defaultName: String, onResult: (error: String?, newId: String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val raw = runCatching {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            val newId = raw?.let {
                runCatching {
                    val export = container.templateJson.decode(it)
                    repo.createFrom(export.name, export.spec, export.elements, defaultName).id
                }.getOrNull()
            }
            // Return the failure reason as a short detail; the UI prepends "Import fehlgeschlagen: %1$s"
            // in front of it, so do NOT return the full message here (would be duplicated).
            withContext(Dispatchers.Main) {
                when {
                    raw == null -> onResult(app.getString(R.string.err_file_not_readable), null)
                    newId == null -> onResult(app.getString(R.string.err_file_invalid), null)
                    else -> onResult(null, newId)
                }
            }
        }
    }
}
