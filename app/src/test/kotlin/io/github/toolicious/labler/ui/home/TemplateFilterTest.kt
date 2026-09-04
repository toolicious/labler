package io.github.toolicious.labler.ui.home

import io.github.toolicious.labler.data.TemplateSort
import io.github.toolicious.labler.model.BarcodeElement
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.PrinterFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering and narrowing of the overview. The measured length is handed in rather than rendered,
 * which is exactly how the screen uses it: a variable label carries only its minimum, so its real
 * length has to come from outside.
 */
class TemplateFilterTest {

    private fun template(
        name: String,
        favorite: Boolean = false,
        prints: Int = 0,
        updatedAt: Long = 0L,
        widthMm: Int = 12,
        media: MediaType = MediaType.DIE_CUT,
        family: PrinterFamily = PrinterFamily.PHOMEMO,
        code: Boolean = false,
    ) = LabelTemplate(
        id = name,
        name = name,
        spec = LabelSpec(tapeWidthMm = widthMm, media = media, family = family),
        elements = if (code) listOf(BarcodeElement(id = "b")) else emptyList(),
        favorite = favorite,
        printCount = prints,
        updatedAt = updatedAt,
    )

    private fun measured(template: LabelTemplate, lengthMm: Int = 40) =
        MeasuredTemplate(template, lengthMm)

    private fun namesSortedBy(
        templates: List<LabelTemplate>,
        sort: TemplateSort,
        ascending: Boolean,
    ) = templates.sortedWith(templateComparator(sort, ascending)).map { it.name }

    // --- sorting -----------------------------------------------------------------------------

    @Test
    fun `favorites stay on top whatever is sorted by`() {
        val all = listOf(
            template("Anton"),
            template("Berta", favorite = true),
            template("Cesar"),
            template("Dora", favorite = true),
        )
        assertEquals(
            listOf("Berta", "Dora", "Anton", "Cesar"),
            namesSortedBy(all, TemplateSort.NAME, ascending = true),
        )
        // Turning the direction around must not lift the plain ones above the favorites.
        assertEquals(
            listOf("Dora", "Berta", "Cesar", "Anton"),
            namesSortedBy(all, TemplateSort.NAME, ascending = false),
        )
    }

    @Test
    fun `an umlaut sorts beside its base letter, not behind Z`() {
        val all = listOf(template("Banane"), template("Äpfel"))
        assertEquals(
            listOf("Äpfel", "Banane"),
            namesSortedBy(all, TemplateSort.NAME, ascending = true),
        )
    }

    @Test
    fun `prints and edit date sort by their own number`() {
        val all = listOf(
            template("wenig", prints = 1, updatedAt = 300),
            template("viel", prints = 9, updatedAt = 100),
            template("mittel", prints = 5, updatedAt = 200),
        )
        assertEquals(
            listOf("viel", "mittel", "wenig"),
            namesSortedBy(all, TemplateSort.PRINTS, ascending = false),
        )
        assertEquals(
            listOf("wenig", "mittel", "viel"),
            namesSortedBy(all, TemplateSort.UPDATED, ascending = false),
        )
    }

    @Test
    fun `labels that tie keep a fixed order instead of shuffling`() {
        val all = listOf(template("Zeta"), template("Alpha"), template("Mitte"))
        val once = namesSortedBy(all, TemplateSort.PRINTS, ascending = false)
        assertEquals(listOf("Alpha", "Mitte", "Zeta"), once)
        assertEquals(once, namesSortedBy(all.reversed(), TemplateSort.PRINTS, ascending = false))
    }

    // --- filtering ---------------------------------------------------------------------------

    @Test
    fun `an empty filter lets everything through`() {
        val filter = TemplateFilter()
        assertTrue(filter.isEmpty)
        assertEquals(0, filter.activeCount)
        assertTrue(filter.matches(measured(template("irgendwas"))))
    }

    @Test
    fun `options inside a group are alternatives`() {
        val filter = TemplateFilter(tapeWidthsMm = setOf(9, 12))
        assertTrue(filter.matches(measured(template("neun", widthMm = 9))))
        assertTrue(filter.matches(measured(template("zwoelf", widthMm = 12))))
        assertFalse(filter.matches(measured(template("vierundzwanzig", widthMm = 24))))
    }

    @Test
    fun `groups have to hold at the same time`() {
        val filter = TemplateFilter(
            tapeWidthsMm = setOf(12),
            media = setOf(MediaType.CONTINUOUS),
        )
        assertTrue(
            filter.matches(measured(template("passt", widthMm = 12, media = MediaType.CONTINUOUS)))
        )
        // Right width, wrong paper.
        assertFalse(
            filter.matches(measured(template("stanz", widthMm = 12, media = MediaType.DIE_CUT)))
        )
        // Right paper, wrong width.
        assertFalse(
            filter.matches(measured(template("breit", widthMm = 24, media = MediaType.CONTINUOUS)))
        )
    }

    @Test
    fun `the length filter goes by the measured length, not the stored one`() {
        val filter = TemplateFilter(lengths = setOf(LengthBucket.OVER_100))
        // Same label, its spec still carrying the default minimum of 40 mm.
        assertTrue(filter.matches(measured(template("lang"), lengthMm = 180)))
        assertFalse(filter.matches(measured(template("kurz"), lengthMm = 40)))
    }

    @Test
    fun `length classes meet without a gap and without overlapping`() {
        assertEquals(LengthBucket.UP_TO_25, LengthBucket.of(0))
        assertEquals(LengthBucket.UP_TO_25, LengthBucket.of(25))
        assertEquals(LengthBucket.UP_TO_50, LengthBucket.of(26))
        assertEquals(LengthBucket.UP_TO_50, LengthBucket.of(50))
        assertEquals(LengthBucket.UP_TO_100, LengthBucket.of(51))
        assertEquals(LengthBucket.UP_TO_100, LengthBucket.of(100))
        assertEquals(LengthBucket.OVER_100, LengthBucket.of(101))
        assertEquals(LengthBucket.OVER_100, LengthBucket.of(5_000))
    }

    @Test
    fun `favorites and code narrow on their own`() {
        val plain = measured(template("schlicht"))
        val starred = measured(template("stern", favorite = true))
        val coded = measured(template("qr", code = true))

        assertTrue(TemplateFilter(favoritesOnly = true).matches(starred))
        assertFalse(TemplateFilter(favoritesOnly = true).matches(plain))
        assertTrue(TemplateFilter(withCode = true).matches(coded))
        assertFalse(TemplateFilter(withCode = true).matches(plain))
    }

    @Test
    fun `every single choice counts towards the badge`() {
        val filter = TemplateFilter(
            media = setOf(MediaType.DIE_CUT),
            tapeWidthsMm = setOf(9, 12),
            lengths = setOf(LengthBucket.OVER_100),
            favoritesOnly = true,
        )
        assertEquals(5, filter.activeCount)
        assertFalse(filter.isEmpty)
    }

    // --- facets ------------------------------------------------------------------------------

    @Test
    fun `only what actually occurs is offered`() {
        val items = listOf(
            measured(template("a", widthMm = 12, media = MediaType.DIE_CUT), lengthMm = 20),
            measured(template("b", widthMm = 9, media = MediaType.DIE_CUT), lengthMm = 200),
        )
        val facets = facetsOf(items)

        assertEquals(listOf(9, 12), facets.tapeWidthsMm.map { it.value })
        assertEquals(listOf(MediaType.DIE_CUT), facets.media.map { it.value })
        assertEquals(
            listOf(LengthBucket.UP_TO_25, LengthBucket.OVER_100),
            facets.lengths.map { it.value },
        )
        assertEquals(listOf(PrinterFamily.PHOMEMO), facets.families.map { it.value })
        assertFalse(facets.hasCode)
        assertFalse(facets.hasFavorites)
    }

    @Test
    fun `a count is the plain number of labels behind that chip`() {
        val items = listOf(
            measured(template("a", widthMm = 12, media = MediaType.DIE_CUT)),
            measured(template("b", widthMm = 12, media = MediaType.CONTINUOUS)),
            measured(template("c", widthMm = 9, media = MediaType.CONTINUOUS), lengthMm = 200),
            measured(template("d", widthMm = 9, favorite = true, code = true)),
        )
        val facets = facetsOf(items)

        assertEquals(listOf(9 to 2, 12 to 2), facets.tapeWidthsMm.map { it.value to it.count })
        assertEquals(
            listOf(MediaType.DIE_CUT to 2, MediaType.CONTINUOUS to 2),
            facets.media.map { it.value to it.count },
        )
        assertEquals(
            listOf(LengthBucket.UP_TO_50 to 3, LengthBucket.OVER_100 to 1),
            facets.lengths.map { it.value to it.count },
        )
        assertEquals(1, facets.favoritesCount)
        assertEquals(1, facets.codeCount)
    }

    @Test
    fun `an option is offered only when something is behind it, so no chip reads zero`() {
        val items = listOf(
            measured(template("a", widthMm = 12, media = MediaType.DIE_CUT), lengthMm = 30),
            measured(template("b", widthMm = 12, media = MediaType.CONTINUOUS), lengthMm = 30),
        )
        val facets = facetsOf(items)

        // Only the one length class that anything falls into, and both papers.
        assertEquals(listOf(LengthBucket.UP_TO_50), facets.lengths.map { it.value })
        assertEquals(2, facets.media.size)
        assertTrue(
            (facets.media + facets.tapeWidthsMm + facets.lengths + facets.families)
                .all { it.count > 0 }
        )
    }

    @Test
    fun `nothing to choose from means an empty sheet`() {
        val only = listOf(measured(template("einziges")))
        assertTrue(facetsOf(only).isEmpty)

        val two = only + measured(template("anderes", widthMm = 9))
        assertFalse(facetsOf(two).isEmpty)
    }
}
