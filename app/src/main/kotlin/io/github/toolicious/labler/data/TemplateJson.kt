package io.github.toolicious.labler.data

import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Self-contained export format for templates (backup, sharing, device migration).
 * "images" is reserved for future image elements (Base64), empty in v1.
 */
@Serializable
data class TemplateExport(
    val formatVersion: Int = 1,
    val name: String,
    val spec: LabelSpec,
    val elements: List<LabelElement> = emptyList(),
    val images: Map<String, String> = emptyMap(),
)

class TemplateJson(private val json: Json) {

    fun encode(template: LabelTemplate): String = json.encodeToString(
        TemplateExport(
            formatVersion = 1,
            name = template.name,
            spec = template.spec,
            elements = template.elements,
        )
    )

    fun decode(raw: String): TemplateExport {
        val export = json.decodeFromString<TemplateExport>(raw)
        require(export.formatVersion >= 1) { "Unknown format" }
        return export
    }
}
