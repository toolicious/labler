package io.github.toolicious.labler.model

/**
 * Language-neutral placeholders in text elements, resolved at print time:
 * {date} {time} {#} {#:3} (with leading zeros) {var:Label}.
 * The old German tokens ({datum} {zeit} {nr} {frage:...}) are still
 * recognized for compatibility.
 */
object Placeholders {

    val TOKEN_REGEX = Regex(
        "\\{(date|time|#(?::\\d+)?|var:[^}]*|datum|zeit|nr(?::\\d+)?|frage:[^}]*)\\}"
    )
    private val COUNTER_REGEX = Regex("\\{(?:#|nr)(?::\\d+)?\\}")
    private val QUESTION_REGEX = Regex("\\{(?:var|frage):([^}]*)\\}")

    data class Context(
        val dateText: String,
        val timeText: String,
        val counter: Int,
        val answers: Map<String, String> = emptyMap(),
    )

    /** Placeholder-capable text field of an element (text content or barcode content). */
    private fun tokenTextOf(el: LabelElement): String? = when (el) {
        is TextElement -> el.text
        is BarcodeElement -> el.data
        else -> null
    }

    fun containsAny(elements: List<LabelElement>): Boolean =
        elements.any { tokenTextOf(it)?.let { t -> TOKEN_REGEX.containsMatchIn(t) } == true }

    fun containsCounter(elements: List<LabelElement>): Boolean =
        elements.any { tokenTextOf(it)?.let { t -> COUNTER_REGEX.containsMatchIn(t) } == true }

    /** All free-text questions (labels) in order, deduplicated. */
    fun questions(elements: List<LabelElement>): List<String> =
        elements.mapNotNull { tokenTextOf(it) }
            .flatMap { QUESTION_REGEX.findAll(it).map { m -> m.groupValues[1].trim() } }
            .filter { it.isNotEmpty() }
            .distinct()

    fun resolve(elements: List<LabelElement>, context: Context): List<LabelElement> =
        elements.map { element ->
            val text = tokenTextOf(element)
            if (text == null || !TOKEN_REGEX.containsMatchIn(text)) return@map element
            val resolved = resolveText(text, context)
            when (element) {
                is TextElement -> element.copy(text = resolved)
                is BarcodeElement -> element.copy(data = resolved)
                else -> element
            }
        }

    fun resolveText(text: String, context: Context): String =
        TOKEN_REGEX.replace(text) { match ->
            val token = match.groupValues[1]
            when {
                token == "date" || token == "datum" -> context.dateText
                token == "time" || token == "zeit" -> context.timeText
                token == "#" || token == "nr" -> context.counter.toString()
                token.startsWith("#:") || token.startsWith("nr:") -> {
                    val width = token.substringAfter(':').toIntOrNull() ?: 0
                    context.counter.toString().padStart(width, '0')
                }
                token.startsWith("var:") ->
                    context.answers[token.substringAfter("var:").trim()] ?: ""
                token.startsWith("frage:") ->
                    context.answers[token.substringAfter("frage:").trim()] ?: ""
                else -> match.value
            }
        }
}
