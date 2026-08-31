package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhomemoProtocolTest {

    private fun hex(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    @Test
    fun `complete die-cut job for a white 8-dot label has exactly the golden bytes`() {
        val job = PhomemoProtocol.buildJob(MonoImage.blank(8, PhomemoProtocol.HEAD_DOTS), MediaType.DIE_CUT)

        val expected = hex(0x10, 0xFF, 0x40) +                       // Init
            ByteArray(15) +                                          // Padding
            hex(0x10, 0xFF, 0xF1, 0x02) +                            // Print-Start
            hex(0x1D, 0x76, 0x30, 0x00) +                            // GS v 0
            hex(0x0C, 0x00) +                                        // xL xH = 12 bytes/line
            hex(0x08, 0x00) +                                        // yL yH = 8 lines
            ByteArray(96) +                                          // 8 columns x 12 bytes
            hex(0x1D, 0x0C) +                                        // Form-Feed
            hex(0x10, 0xFF, 0xF1, 0x45) +                            // Print-End
            hex(0x10, 0xFF, 0x40) +                                  // Init
            hex(0x10, 0xFF, 0x40)                                    // Init

        assertEquals(138, job.size)
        assertContentEquals(expected, job)
    }

    @Test
    fun `continuous job ends with feed 91 and print-end`() {
        val job = PhomemoProtocol.buildJob(MonoImage.blank(8, PhomemoProtocol.HEAD_DOTS), MediaType.CONTINUOUS)
        val tail = job.copyOfRange(job.size - 7, job.size)
        assertContentEquals(hex(0x1B, 0x4A, 0x5B, 0x10, 0xFF, 0xF1, 0x45), tail)
    }

    @Test
    fun `label length is stored little-endian in the header`() {
        val job = PhomemoProtocol.buildJob(MonoImage.blank(320, PhomemoProtocol.HEAD_DOTS), MediaType.DIE_CUT)
        // Header: 3 Init + 15 Padding + 4 Start + 4 GSv0 + xL xH, then yL yH
        assertEquals(0x40.toByte(), job[28])
        assertEquals(0x01.toByte(), job[29])
    }

    @Test
    fun `payload size is 12 bytes per column`() {
        val job = PhomemoProtocol.buildJob(MonoImage.blank(320, PhomemoProtocol.HEAD_DOTS), MediaType.DIE_CUT)
        assertEquals(3 + 15 + 4 + 4 + 2 + 2 + 320 * 12 + 12, job.size)
    }

    @Test
    fun `default job has no density command (byte-identical whether density is omitted or null)`() {
        val omitted = PhomemoProtocol.buildJob(MonoImage.blank(8, PhomemoProtocol.HEAD_DOTS), MediaType.DIE_CUT)
        val explicitNull = PhomemoProtocol.buildJob(MonoImage.blank(8, PhomemoProtocol.HEAD_DOTS), MediaType.DIE_CUT, null)
        assertEquals(138, omitted.size)
        assertContentEquals(omitted, explicitNull)
    }

    @Test
    fun `density command is inserted right after init and adds four bytes`() {
        val job = PhomemoProtocol.buildJob(MonoImage.blank(8, PhomemoProtocol.HEAD_DOTS), MediaType.DIE_CUT, density = 8)
        // Init (3) then the density command 1F 70 01 08 (4), before the header padding.
        assertContentEquals(hex(0x10, 0xFF, 0x40, 0x1F, 0x70, 0x01, 0x08), job.copyOfRange(0, 7))
        assertEquals(138 + 4, job.size)
    }

    @Test
    fun `density level must be within 1 to 15`() {
        assertFailsWith<IllegalArgumentException> { PhomemoProtocol.density(0) }
        assertFailsWith<IllegalArgumentException> { PhomemoProtocol.density(16) }
        assertContentEquals(hex(0x1F, 0x70, 0x01, 0x0F), PhomemoProtocol.density(15))
    }
}
