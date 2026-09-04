package io.github.toolicious.labler.ui.home

import io.github.toolicious.labler.data.TemplateSort
import io.github.toolicious.labler.model.BarcodeElement
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.PrinterFamily
import java.text.Collator

/**
 * A label together with the length it actually reaches. A variable label stores only its minimum,
 * so the stored one would file it under the wrong length; the measuring happens once per list
 * rather than once per tap on a filter.
 */
data class MeasuredTemplate(val template: LabelTemplate, val lengthMm: Int)

/**
 * Length classes the filter offers. The boundaries are fixed rather than derived from what is
 * stored, so a label always falls into the same drawer; which drawers are offered does depend on
 * what is there.
 */
enum class LengthBucket(val range: IntRange) {
    UP_TO_25(0..25),
    UP_TO_50(26..50),
    UP_TO_100(51..100),
    OVER_100(101..Int.MAX_VALUE),
    ;

    operator fun contains(lengthMm: Int) = lengthMm in range

    companion object {
        /** The class [lengthMm] belongs to. Anything below the first boundary counts as shortest. */
        fun of(lengthMm: Int): LengthBucket = entries.firstOrNull { lengthMm in it } ?: UP_TO_25
    }
}

/** Whether a label carries a QR code or a barcode, the one content the filter asks about. */
val LabelTemplate.hasCode: Boolean get() = elements.any { it is BarcodeElement }

/**
 * What the overview is narrowed down to. An empty group does not restrict at all; inside a group
 * the options are alternatives, between the groups every one of them has to hold.
 */
data class TemplateFilter(
    val media: Set<MediaType> = emptySet(),
    val tapeWidthsMm: Set<Int> = emptySet(),
    val lengths: Set<LengthBucket> = emptySet(),
    val families: Set<PrinterFamily> = emptySet(),
    val favoritesOnly: Boolean = false,
    val withCode: Boolean = false,
) {
    /** Choices in force, which is the number the badge on the filter icon carries. */
    val activeCount: Int =
        media.size + tapeWidthsMm.size + lengths.size + families.size +
            (if (favoritesOnly) 1 else 0) + (if (withCode) 1 else 0)

    val isEmpty: Boolean get() = activeCount == 0

    fun matches(item: MeasuredTemplate): Boolean {
        val spec = item.template.spec
        if (media.isNotEmpty() && spec.media !in media) return false
        if (tapeWidthsMm.isNotEmpty() && spec.tapeWidthMm !in tapeWidthsMm) return false
        if (lengths.isNotEmpty() && LengthBucket.of(item.lengthMm) !in lengths) return false
        if (families.isNotEmpty() && spec.family !in families) return false
        if (favoritesOnly && !item.template.favorite) return false
        if (withCode && !item.template.hasCode) return false
        return true
    }
}

/** One choice on a chip, with the number of labels it would leave. */
data class FacetOption<T>(val value: T, val count: Int)

/**
 * The options worth offering and how many labels carry each of them. A group with only one option
 * would narrow nothing and is left out entirely.
 *
 * A count is the plain number behind that one option and takes no notice of what is set elsewhere,
 * so it does not move around while filters are being set. Options and counts come from the same
 * list, which means every chip on offer has at least one label behind it.
 */
data class TemplateFacets(
    val media: List<FacetOption<MediaType>> = emptyList(),
    val tapeWidthsMm: List<FacetOption<Int>> = emptyList(),
    val lengths: List<FacetOption<LengthBucket>> = emptyList(),
    val families: List<FacetOption<PrinterFamily>> = emptyList(),
    val favoritesCount: Int = 0,
    val codeCount: Int = 0,
) {
    val hasFavorites: Boolean get() = favoritesCount > 0
    val hasCode: Boolean get() = codeCount > 0

    /** True when there is nothing to choose, and the filter button would open an empty sheet. */
    val isEmpty: Boolean
        get() = media.size < 2 && tapeWidthsMm.size < 2 && lengths.size < 2 &&
            families.size < 2 && !hasCode && !hasFavorites
}

fun facetsOf(items: List<MeasuredTemplate>): TemplateFacets {
    val specs = items.map { it.template.spec }
    fun <T> options(values: List<T>, of: (MeasuredTemplate) -> T) = values
        .map { value -> FacetOption(value, items.count { of(it) == value }) }
        .filter { it.count > 0 }
    return TemplateFacets(
        media = options(MediaType.entries) { it.template.spec.media },
        tapeWidthsMm = options(specs.map { it.tapeWidthMm }.distinct().sorted()) {
            it.template.spec.tapeWidthMm
        },
        lengths = options(LengthBucket.entries) { LengthBucket.of(it.lengthMm) },
        families = options(PrinterFamily.entries) { it.template.spec.family },
        favoritesCount = items.count { it.template.favorite },
        codeCount = items.count { it.template.hasCode },
    )
}

/** Narrows to what the search field is looking for, the step that runs before any filter. */
fun List<MeasuredTemplate>.searched(query: String): List<MeasuredTemplate> {
    val needle = query.trim()
    return if (needle.isBlank()) this
    else filter { it.template.name.contains(needle, ignoreCase = true) }
}

/**
 * Order of the overview. Favorites stay pinned on top whatever is picked, so the star keeps meaning
 * "keep this one within reach" rather than turning into a mere marker.
 */
fun templateComparator(sort: TemplateSort, ascending: Boolean): Comparator<LabelTemplate> {
    // Locale-aware, otherwise an umlaut sorts behind Z instead of beside its base letter.
    val collator = Collator.getInstance()
    val byName = Comparator<LabelTemplate> { a, b -> collator.compare(a.name, b.name) }
    val byCriterion: Comparator<LabelTemplate> = when (sort) {
        TemplateSort.NAME -> byName
        TemplateSort.UPDATED -> compareBy { it.updatedAt }
        TemplateSort.PRINTS -> compareBy { it.printCount }
    }
    // The name has the last word, so labels that tie (two never printed, say) keep a fixed order
    // instead of shuffling on every emission.
    return compareByDescending<LabelTemplate> { it.favorite }
        .then(if (ascending) byCriterion else byCriterion.reversed())
        .then(byName)
}
