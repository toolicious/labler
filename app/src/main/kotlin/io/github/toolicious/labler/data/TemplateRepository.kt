package io.github.toolicious.labler.data

import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.PrinterFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

/** Current version of the element JSON schema (migration hook from day 1). */
private const val SCHEMA_VERSION = 1

class TemplateRepository(private val dao: TemplateDao, private val json: Json) {

    fun observeAll(): Flow<List<LabelTemplate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): LabelTemplate? = dao.getById(id)?.toDomain()

    suspend fun create(name: String, spec: LabelSpec, defaultName: String): LabelTemplate =
        createFrom(name, spec, emptyList(), defaultName)

    suspend fun createFrom(
        name: String,
        spec: LabelSpec,
        elements: List<LabelElement>,
        defaultName: String,
    ): LabelTemplate {
        val now = System.currentTimeMillis()
        val template = LabelTemplate(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { defaultName },
            spec = spec,
            elements = elements,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(template.toEntity())
        return template
    }

    suspend fun save(template: LabelTemplate) {
        dao.upsert(template.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun duplicate(id: String, newName: String): LabelTemplate? {
        val source = get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            favorite = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(copy.toEntity())
        return copy
    }

    suspend fun rename(id: String, name: String) =
        dao.rename(id, name.ifBlank { "Label" }, System.currentTimeMillis())

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun setCounter(id: String, value: Int) = dao.setCounter(id, value)

    /** Counts [copies] more labels printed from this template. */
    suspend fun addPrints(id: String, copies: Int) = dao.addPrints(id, copies)

    suspend fun delete(id: String) = dao.delete(id)

    /** Snapshot of all templates (for the backup export). */
    suspend fun getAll(): List<LabelTemplate> = dao.getAllOnce().map { it.toDomain() }

    /** Inserts a template unchanged (for the backup import). */
    suspend fun insert(template: LabelTemplate) = dao.upsert(template.toEntity())

    /** Deletes all templates (for the replace import). */
    suspend fun deleteAll() = dao.deleteAll()

    // ----- Mapping -----

    private fun TemplateEntity.toDomain(): LabelTemplate = LabelTemplate(
        id = id,
        name = name,
        spec = LabelSpec(
            tapeWidthMm = tapeWidthMm,
            lengthMm = lengthMm,
            media = runCatching { MediaType.valueOf(media) }.getOrDefault(MediaType.DIE_CUT),
            autoLength = autoLength,
            manualEdges = manualEdges,
            leadingMm = leadingMm,
            marginPx = marginPx,
            family = PrinterFamily.ofName(family),
        ),
        elements = decodeElements(schemaVersion, elementsJson),
        favorite = favorite,
        counterValue = counterValue,
        printCount = printCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun LabelTemplate.toEntity(): TemplateEntity = TemplateEntity(
        id = id,
        name = name,
        tapeWidthMm = spec.tapeWidthMm,
        lengthMm = spec.lengthMm,
        media = spec.media.name,
        autoLength = spec.autoLength,
        manualEdges = spec.manualEdges,
        leadingMm = spec.leadingMm,
        marginPx = spec.marginPx,
        family = spec.family.name,
        elementsJson = json.encodeToString(elements),
        schemaVersion = SCHEMA_VERSION,
        favorite = favorite,
        counterValue = counterValue,
        printCount = printCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun decodeElements(version: Int, raw: String): List<LabelElement> =
        runCatching { json.decodeFromString<List<LabelElement>>(migrateJson(version, raw)) }
            .getOrDefault(emptyList())

    /** Hook for future schema migrations; v1 is the identity. */
    private fun migrateJson(version: Int, raw: String): String = raw
}
