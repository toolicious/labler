package io.github.toolicious.labler.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    @Test
    fun `wifi payload with escaping`() {
        val fields = mapOf(
            QrPayload.SSID to "My;Net",
            QrPayload.PASSWORD to "p:a,ss",
            QrPayload.AUTH to "WPA",
            QrPayload.HIDDEN to "false",
        )
        assertEquals(
            "WIFI:T:WPA;S:My\\;Net;P:p\\:a\\,ss;H:false;;",
            QrPayload.build(QrPayloadType.WIFI, fields),
        )
    }

    @Test
    fun `wifi defaults to WPA when auth blank`() {
        val out = QrPayload.build(QrPayloadType.WIFI, mapOf(QrPayload.SSID to "Home"))
        assertEquals("WIFI:T:WPA;S:Home;P:;H:false;;", out)
    }

    @Test
    fun `contact mecard omits blank fields`() {
        val fields = mapOf(QrPayload.NAME to "Jo", QrPayload.PHONE to "0123")
        assertEquals("MECARD:N:Jo;TEL:0123;;", QrPayload.build(QrPayloadType.CONTACT, fields))
    }

    @Test
    fun `wifi password validation`() {
        assertTrue(QrPayload.isWifiPasswordValid("nopass", ""))
        assertTrue(QrPayload.isWifiPasswordValid("WPA", "12345678"))
        assertFalse(QrPayload.isWifiPasswordValid("WPA", "short"))
        assertTrue(QrPayload.isWifiPasswordValid("WEP", "12345"))
        assertTrue(QrPayload.isWifiPasswordValid("WEP", "0123456789"))
        assertFalse(QrPayload.isWifiPasswordValid("WEP", "123456"))
    }

    @Test
    fun `cleared text does not reappear when switching to link`() {
        val typed = BarcodeElement(id = "1", data = "hello", payload = mapOf(QrPayload.TEXT to "hello"))
        val toLink = QrPayload.switchType(typed, QrPayloadType.LINK)
        assertEquals("hello", toLink.payload[QrPayload.URL]) // Text carried over to Link
        val backToText = QrPayload.switchType(toLink, QrPayloadType.TEXT)
        val cleared = backToText.copy(data = "", payload = backToText.payload + (QrPayload.TEXT to ""))
        val toLink2 = QrPayload.switchType(cleared, QrPayloadType.LINK)
        assertNull(toLink2.payload[QrPayload.URL]) // cleared value does not reappear, and no auto-prefill
        assertEquals("", toLink2.data)
    }

    @Test
    fun `link auto https is dropped when switching away, real url kept`() {
        val auto = BarcodeElement(id = "1", payloadType = QrPayloadType.LINK, data = "https://", payload = mapOf(QrPayload.URL to "https://"))
        val toText = QrPayload.switchType(auto, QrPayloadType.TEXT)
        assertEquals("", toText.data)
        assertNull(toText.payload[QrPayload.TEXT]) // the stray "https://" is not carried
        val real = BarcodeElement(id = "1", payloadType = QrPayloadType.LINK, data = "https://x.io", payload = mapOf(QrPayload.URL to "https://x.io"))
        val toText2 = QrPayload.switchType(real, QrPayloadType.TEXT)
        assertEquals("https://x.io", toText2.data) // a real URL is kept
    }

    @Test
    fun `link email phone`() {
        assertEquals("https://x.io", QrPayload.build(QrPayloadType.LINK, mapOf(QrPayload.URL to "https://x.io")))
        assertEquals("mailto:a@b.c", QrPayload.build(QrPayloadType.EMAIL, mapOf(QrPayload.EMAIL to "a@b.c")))
        assertEquals("tel:+49123", QrPayload.build(QrPayloadType.PHONE, mapOf(QrPayload.PHONE to "+49123")))
    }
}
