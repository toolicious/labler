package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PrinterProtocolsTest {

    @Test
    fun `the default framing is the plain chunker`() {
        val job = ByteArray(250) { it.toByte() }
        val framed = PhomemoProtocol.framePayload(job, PhomemoProtocol.transport.chunkSize)
        val chunked = Chunker.chunk(job, PhomemoProtocol.transport.chunkSize)
        assertEquals(chunked.size, framed.size)
        framed.forEachIndexed { i, bytes -> assertContentEquals(chunked[i], bytes, "chunk $i") }
    }

    @Test
    fun `an advertised name resolves to its family`() {
        assertSame(PhomemoProtocol, PrinterProtocols.matchName("P15_1234_BLE"))
        assertSame(PhomemoProtocol, PrinterProtocols.matchName("P12_Z7EAE_BLE"))
        assertSame(PhomemoProtocol, PrinterProtocols.matchName("L13_9F_BLE"))
        assertNull(PrinterProtocols.matchName("Some other speaker"))
    }

    @Test
    fun `the BLE suffix is hidden from the user but the rest of the name is kept`() {
        assertEquals("P15_1234", PhomemoProtocol.ble.displayName("P15_1234_BLE"))
        assertEquals("P15_1234", PhomemoProtocol.ble.displayName("P15_1234"))
    }

    @Test
    fun `an unknown family name from storage falls back to Phomemo`() {
        assertEquals(PrinterFamily.PHOMEMO, PrinterFamily.ofName(null))
        assertEquals(PrinterFamily.PHOMEMO, PrinterFamily.ofName("SOMETHING_NEWER"))
        assertEquals(PrinterFamily.PHOMEMO, PrinterFamily.ofName("PHOMEMO"))
    }
}
