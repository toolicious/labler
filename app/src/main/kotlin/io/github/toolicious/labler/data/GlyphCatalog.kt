package io.github.toolicious.labler.data

import android.content.Context
import io.github.toolicious.labler.model.IconFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One insertable glyph, whatever it came from. A glyph out of a bundled icon font carries the
 * [fontKey] of that font, plain Unicode symbols and emoji leave it null.
 *
 * [name] and [keywords] are expected in lower case; the search compares against them directly
 * rather than folding case on every entry it walks past.
 */
data class Glyph(
    val glyph: String,
    val name: String,
    /**
     * The keywords as a single line fenced by separators, ",cat,dog,paw print,", or empty when
     * there are none. One string per glyph rather than a list of them, because the icon catalog
     * alone holds fifty thousand keywords: as separate strings they cost about two megabytes in
     * object overhead, and every keystroke walked every one of them. Fenced, a whole keyword is
     * ",term," and one starting with the term is ",term", so the search is a plain contains.
     */
    val keywords: String = "",
    val fontKey: String? = null,
) {
    companion object {
        /** Builds the fenced form of [keywords] for a source that has them as separate words. */
        fun fence(keywords: List<String>): String =
            if (keywords.isEmpty()) "" else keywords.joinToString(",", prefix = ",", postfix = ",")
    }
}

/**
 * Ranks glyphs against what the user typed. Deliberately knows nothing about icons: the emoji and
 * the built-in Unicode symbols are meant to feed the same search later, at which point this gets
 * handed the union of all three sets and the search field moves up out of the icon tab.
 */
object GlyphSearch {

    /** A search term with the forms the scoring needs, built once instead of per entry. */
    private class Term(val text: String) {
        val nameWord = "_" + text
        val wholeKeyword = "," + text + ","
        val keywordStart = "," + text
    }

    /** Matches every term, ranked best first. A blank query returns [entries] untouched. */
    fun match(entries: List<Glyph>, query: String, limit: Int = 400): List<Glyph> {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }.map(::Term)
        if (terms.isEmpty()) return entries
        val hits = ArrayList<Pair<Int, Glyph>>()
        for (entry in entries) {
            var total = 0
            for (term in terms) {
                val score = score(entry, term)
                if (score == 0) {
                    total = 0
                    break
                }
                total += score
            }
            if (total > 0) hits += total to entry
        }
        hits.sortWith(compareByDescending<Pair<Int, Glyph>> { it.first }.thenBy { it.second.name })
        return hits.take(limit).map { it.second }
    }

    /**
     * How well one term fits one glyph. The name is worth more than a keyword, and the start of a
     * word more than the middle, so that "car" leads with the icon actually called car rather than
     * with everything tagged "carousel".
     */
    private fun score(entry: Glyph, term: Term): Int = when {
        entry.name == term.text -> 100
        entry.name.startsWith(term.text) -> 80
        entry.name.contains(term.nameWord) -> 70
        entry.keywords.contains(term.wholeKeyword) -> 60
        entry.name.contains(term.text) -> 50
        entry.keywords.contains(term.keywordStart) -> 40
        entry.keywords.contains(term.text) -> 20
        else -> 0
    }
}

/**
 * The bundled icon font as searchable glyphs, read from an asset that holds one icon per line:
 * name, codepoint in hex and the search keywords, separated by tabs. Parsed once off the main
 * thread and kept, because the picker is opened again and again.
 */
object IconCatalog {

    private const val ASSET = "icons/material_icons.txt"

    @Volatile
    private var cached: List<Glyph>? = null

    suspend fun load(context: Context): List<Glyph> {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching {
                context.assets.open(ASSET).bufferedReader().useLines { lines ->
                    lines.mapNotNull(::parse).toList()
                }
            }.getOrDefault(emptyList())
            cached = parsed
            parsed
        }
    }

    private fun parse(line: String): Glyph? {
        val parts = line.split('\t')
        if (parts.size < 2) return null
        val codepoint = parts[1].toIntOrNull(16) ?: return null
        // The asset already separates the keywords by commas, so fencing is all that is left to do.
        val keywords = parts.getOrNull(2).orEmpty()
        return Glyph(
            glyph = String(Character.toChars(codepoint)),
            name = parts[0],
            keywords = if (keywords.isEmpty()) "" else "," + keywords + ",",
            fontKey = IconFonts.MATERIAL,
        )
    }
}
