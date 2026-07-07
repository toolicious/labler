package io.github.toolicious.labler.data

import io.github.toolicious.labler.model.LabelTemplate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class BackupSettings(
    val defaultTapeWidthMm: Int? = null,
    val defaultLengthMm: Int? = null,
    val defaultDieCut: Boolean? = null,
    val printerAddress: String? = null,
    val printerName: String? = null,
    val language: String? = null,
)

@Serializable
data class BackupFile(
    val formatVersion: Int = 1,
    val templates: List<LabelTemplate> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

/**
 * Full backup: export all templates plus app settings to a JSON file
 * and read them back in. The templates are self-contained (incl. image Base64).
 */
class BackupRepository(
    private val templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val json: Json,
) {

    suspend fun hasTemplates(): Boolean = templates.getAll().isNotEmpty()

    suspend fun export(languageTag: String?): String {
        val printer = settings.savedPrinter.first()
        val backup = BackupFile(
            formatVersion = 1,
            templates = templates.getAll(),
            settings = BackupSettings(
                defaultTapeWidthMm = settings.defaultTapeWidthMm.first(),
                defaultLengthMm = settings.defaultLengthMm.first(),
                defaultDieCut = settings.defaultDieCut.first(),
                printerAddress = printer?.address,
                printerName = printer?.name,
                language = languageTag,
            ),
        )
        return json.encodeToString(backup)
    }

    /** Reads only the language tag from a backup (the language is applied separately, in the UI). */
    fun peekLanguage(raw: String): String? =
        runCatching { json.decodeFromString<BackupFile>(raw).settings.language }.getOrNull()

    /**
     * Imports a backup. replace = true deletes existing templates and takes over the
     * imported ones with their IDs; otherwise they are added with new IDs.
     * The settings are always applied.
     */
    suspend fun import(raw: String, replace: Boolean) {
        val backup = json.decodeFromString<BackupFile>(raw)
        require(backup.formatVersion >= 1) { "Unknown format" }
        if (replace) templates.deleteAll()
        val now = System.currentTimeMillis()
        backup.templates.forEachIndexed { i, t ->
            val tpl = if (replace) {
                t.copy(updatedAt = now)
            } else {
                t.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now + i)
            }
            templates.insert(tpl)
        }
        val s = backup.settings
        if (s.defaultTapeWidthMm != null && s.defaultLengthMm != null && s.defaultDieCut != null) {
            settings.saveDefaultLabel(s.defaultTapeWidthMm, s.defaultLengthMm, s.defaultDieCut)
        }
        if (s.printerAddress != null) {
            settings.savePrinter(s.printerAddress, s.printerName ?: s.printerAddress)
        }
    }
}
