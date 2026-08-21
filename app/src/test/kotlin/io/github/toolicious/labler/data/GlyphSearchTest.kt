package io.github.toolicious.labler.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphSearchTest {

    private fun glyph(g: String, name: String, vararg keywords: String) =
        Glyph(g, name, Glyph.fence(keywords.toList()))

    private val entries = listOf(
        glyph("A", "car", "vehicle", "automobile"),
        glyph("B", "car_rental", "vehicle", "hire"),
        glyph("C", "add_a_photo", "camera", "picture"),
        glyph("D", "carousel", "slideshow"),
        glyph("E", "pets", "cat", "dog", "paw print"),
    )

    @Test
    fun `a blank query keeps everything in place`() {
        assertEquals(entries, GlyphSearch.match(entries, "   "))
    }

    @Test
    fun `the icon actually called car comes first`() {
        val names = GlyphSearch.match(entries, "car").map { it.name }
        assertEquals("car", names.first())
        assertTrue(names.containsAll(listOf("car_rental", "carousel")))
    }

    @Test
    fun `a keyword finds what the name does not say`() {
        assertEquals(listOf("pets"), GlyphSearch.match(entries, "dog").map { it.name })
    }

    @Test
    fun `every term has to match`() {
        assertEquals(listOf("add_a_photo"), GlyphSearch.match(entries, "add photo").map { it.name })
        assertTrue(GlyphSearch.match(entries, "car photo").isEmpty())
    }

    @Test
    fun `a term inside a multi word keyword still counts`() {
        assertEquals(listOf("pets"), GlyphSearch.match(entries, "paw").map { it.name })
    }

    @Test
    fun `nothing matches an unknown term`() {
        assertTrue(GlyphSearch.match(entries, "zzz").isEmpty())
    }
}
