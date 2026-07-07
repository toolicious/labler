package io.github.toolicious.labler.data

import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.printer.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class PrintHistoryEntry(
    val id: Long,
    val templateId: String?,
    val templateName: String,
    val spec: LabelSpec,
    val elements: List<LabelElement>,
    val copies: Int,
    val printedAt: Long,
)

class HistoryRepository(private val dao: PrintHistoryDao, private val json: Json) {

    fun observeAll(): Flow<List<PrintHistoryEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun record(
        templateId: String?,
        templateName: String,
        spec: LabelSpec,
        resolvedElements: List<LabelElement>,
        copies: Int,
    ) {
        dao.insert(
            PrintHistoryEntity(
                templateId = templateId,
                templateName = templateName,
                tapeWidthMm = spec.tapeWidthMm,
                lengthMm = spec.lengthMm,
                media = spec.media.name,
                elementsJson = json.encodeToString(resolvedElements),
                copies = copies,
                printedAt = System.currentTimeMillis(),
            )
        )
        dao.prune()
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    private fun PrintHistoryEntity.toDomain() = PrintHistoryEntry(
        id = id,
        templateId = templateId,
        templateName = templateName,
        spec = LabelSpec(
            tapeWidthMm = tapeWidthMm,
            lengthMm = lengthMm,
            media = runCatching { MediaType.valueOf(media) }.getOrDefault(MediaType.DIE_CUT),
        ),
        elements = runCatching { json.decodeFromString<List<LabelElement>>(elementsJson) }
            .getOrDefault(emptyList()),
        copies = copies,
        printedAt = printedAt,
    )
}
