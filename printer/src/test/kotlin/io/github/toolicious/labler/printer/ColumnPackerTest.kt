package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnPackerTest {

    @Test
    fun `white image yields only zero bytes`() {
        val packed = ColumnPacker.packColumns(MonoImage.blank(2))
        assertEquals(24, packed.size)
        assertTrue(packed.all { it == 0.toByte() })
    }

    @Test
    fun `top-left pixel lands in the last byte of the first column as LSB`() {
        val img = MonoImage.blank(2)
        img.setBlack(0, 0)
        val packed = ColumnPacker.packColumns(img)
        assertEquals(0x01.toByte(), packed[11])
        packed.forEachIndexed { i, b ->
            if (i != 11) assertEquals(0.toByte(), b, "Byte $i must be 0")
        }
    }

    @Test
    fun `bottom-left pixel lands in the first byte of the first column as MSB`() {
        val img = MonoImage.blank(2)
        img.setBlack(0, 95)
        val packed = ColumnPacker.packColumns(img)
        assertEquals(0x80.toByte(), packed[0])
        packed.forEachIndexed { i, b ->
            if (i != 0) assertEquals(0.toByte(), b, "Byte $i must be 0")
        }
    }

    @Test
    fun `second column starts at byte 12`() {
        val img = MonoImage.blank(2)
        img.setBlack(1, 0)
        val packed = ColumnPacker.packColumns(img)
        assertEquals(0x01.toByte(), packed[23])
        assertEquals(0.toByte(), packed[11])
    }

    @Test
    fun `row 88 is bit 0 of the first byte`() {
        val img = MonoImage.blank(1)
        img.setBlack(0, 88)
        val packed = ColumnPacker.packColumns(img)
        assertEquals(0x01.toByte(), packed[0])
    }
}
