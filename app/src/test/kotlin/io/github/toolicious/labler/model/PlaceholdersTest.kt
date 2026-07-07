package io.github.toolicious.labler.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholdersTest {

    private val ctx = Placeholders.Context(
        dateText = "02.07.2026",
        timeText = "21:30",
        counter = 7,
        answers = mapOf("Name" to "Kiste A"),
    )

    @Test
    fun `replaces all English tokens`() {
        assertEquals(
            "02.07.2026 21:30 Nr 7 = Kiste A",
            Placeholders.resolveText("{date} {time} Nr {#} = {var:Name}", ctx)
        )
    }

    @Test
    fun `German legacy tokens still work`() {
        assertEquals(
            "02.07.2026 007 Kiste A",
            Placeholders.resolveText("{datum} {nr:3} {frage:Name}", ctx)
        )
    }

    @Test
    fun `number with width gets leading zeros`() {
        assertEquals("007", Placeholders.resolveText("{#:3}", ctx))
    }

    @Test
    fun `unknown braces are left in place`() {
        assertEquals("{foo}", Placeholders.resolveText("{foo}", ctx))
    }

    @Test
    fun `variables are found deduplicated`() {
        val elements = listOf(
            TextElement(id = "1", text = "{var:Name} and {var:Place}"),
            TextElement(id = "2", text = "{var:Name}"),
        )
        assertEquals(listOf("Name", "Place"), Placeholders.questions(elements))
        assertTrue(Placeholders.containsAny(elements))
        assertFalse(Placeholders.containsCounter(elements))
    }

    @Test
    fun `counter is detected`() {
        val elements = listOf(TextElement(id = "1", text = "No {#:2}"))
        assertTrue(Placeholders.containsCounter(elements))
    }
}
