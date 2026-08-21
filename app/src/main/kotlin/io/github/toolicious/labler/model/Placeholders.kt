package io.github.toolicious.labler.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Language-neutral placeholders in text elements, resolved at print time:
 * {date} {time} {#} {#:3} (with leading zeros) {var:Label}.
 * A date or time may carry its own format: {date:yyyy-MM-dd}, {time:HH:mm:ss}.
 * The old German tokens ({datum} {zeit} {nr} {frage:...}) are still
 * recognized for compatibility, though only the current ones take a format.
 */
object Placeholders {

    val TOKEN_REGEX = Regex(
        "\\{(date(?::[^}]*)?|time(?::[^}]*)?|#(?::\\d+)?|var:[^}]*|datum|zeit|nr(?::\\d+)?|frage:[^}]*)\\}"
    )
    private val COUNTER_REGEX = Regex("\\{(?:#|nr)(?::\\d+)?\\}")
    private val QUESTION_REGEX = Regex("\\{(?:var|frage):([^}]*)\\}")

    data class Context(
        /** {date} and {time} without a format of their own: whatever the device itself uses. */
        val dateText: String,
        val timeText: String,
        val counter: Int,
        val answers: Map<String, String> = emptyMap(),
        /** The moment being printed. Every date and time token resolves against this one value. */
        val now: Date = Date(),
        /** Locale for the formatted tokens, so MMMM and EEE follow the region of the device. */
        val locale: Locale = Locale.getDefault(),
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
                token.startsWith("date:") -> formatted(token, context, context.dateText, match.value)
                token.startsWith("time:") -> formatted(token, context, context.timeText, match.value)
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

    /**
     * Applies the format the user wrote after the colon. An empty one means the same as the bare
     * token. A format SimpleDateFormat rejects must not fail a print, and quietly substituting a
     * date would hide the typo until a stack of labels is already printed, so the token stays on
     * the label exactly as it was written.
     */
    private fun formatted(token: String, context: Context, plain: String, raw: String): String {
        val pattern = token.substringAfter(':')
        if (pattern.isBlank()) return plain
        return runCatching { SimpleDateFormat(pattern, context.locale).format(context.now) }
            .getOrDefault(raw)
    }
}
