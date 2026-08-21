package io.github.toolicious.labler.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

class PlaceholdersTest {

    private val ctx = Placeholders.Context(
        dateText = "02.07.2026",
        timeText = "21:30",
        counter = 7,
        answers = mapOf("Name" to "Kiste A"),
    )

    // Built in the default time zone, so formatting it back yields these wall clock values wherever
    // the test runs. Locale.US pins the month and weekday names that MMM and EEE produce.
    private val stamped = ctx.copy(
        now = GregorianCalendar(2026, Calendar.JULY, 2, 21, 30, 0).time,
        locale = Locale.US,
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
    fun `date and time take a format of their own`() {
        assertEquals("2026-07-02", Placeholders.resolveText("{date:yyyy-MM-dd}", stamped))
        assertEquals("2 Jul 2026", Placeholders.resolveText("{date:d MMM yyyy}", stamped))
        assertEquals("21:30:00", Placeholders.resolveText("{time:HH:mm:ss}", stamped))
    }

    @Test
    fun `an empty format means the device format`() {
        assertEquals("02.07.2026", Placeholders.resolveText("{date:}", stamped))
        assertEquals("21:30", Placeholders.resolveText("{time:}", stamped))
    }

    @Test
    fun `an unusable format stays on the label`() {
        assertEquals("{date:not a format}", Placeholders.resolveText("{date:not a format}", stamped))
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
