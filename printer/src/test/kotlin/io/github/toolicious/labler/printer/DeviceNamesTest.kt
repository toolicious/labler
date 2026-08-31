package io.github.toolicious.labler.printer

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceNamesTest {

    @Test
    fun `clean name keeps a well formed name untouched`() {
        assertEquals("P12_Z7EAE_BLE", DeviceNames.clean("P12_Z7EAE_BLE"))
    }

    @Test
    fun `clean name drops padding a printer sends along`() {
        // What the reporter of issue 18 saw: a trailing byte that is not valid UTF-8, which
        // Android decodes to the replacement character and which hid the "_BLE" suffix.
        assertEquals("P12_Z7EAE_BLE", DeviceNames.clean("P12_Z7EAE_BLE\uFFFD"))
        assertEquals("P15_1234_BLE", DeviceNames.clean("P15_1234_BLE\u0000\u0000"))
        assertEquals("L13_9F_BLE", DeviceNames.clean("  L13_9F_BLE\t "))
    }
}
