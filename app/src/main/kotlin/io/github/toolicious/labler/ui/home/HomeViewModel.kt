package io.github.toolicious.labler.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val repo = container.templateRepository

    val printerState = container.printerManager.state
    val savedPrinter = container.settings.savedPrinter
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val templates = combine(repo.observeAll(), _query) { list, q ->
        if (q.isBlank()) list
        else list.filter { it.name.contains(q.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
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
            repo.save(current.copy(name = name.ifBlank { current.name }, spec = spec))
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
