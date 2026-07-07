package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkerTest {

    @Test
    fun `splits into 96-byte blocks with remainder`() {
        val payload = ByteArray(250) { it.toByte() }
        val chunks = Chunker.chunk(payload, 96)
        assertEquals(listOf(96, 96, 58), chunks.map { it.size })
        assertContentEquals(payload, chunks.reduce { a, b -> a + b })
    }

    @Test
    fun `empty payload yields no chunks`() {
        assertTrue(Chunker.chunk(ByteArray(0), 96).isEmpty())
    }

    @Test
    fun `exact division without remainder block`() {
        val chunks = Chunker.chunk(ByteArray(192), 96)
        assertEquals(2, chunks.size)
    }
}
