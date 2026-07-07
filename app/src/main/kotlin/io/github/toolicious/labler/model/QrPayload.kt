package io.github.toolicious.labler.model

/**
 * Builds the encoded QR string from a typed payload (WiFi, contact, link, ...). The structured
 * fields live in [BarcodeElement.payload]; this turns them into the string a phone scanner reads.
 */
object QrPayload {

    // Field keys used in the payload map.
    const val TEXT = "text"
    const val URL = "url"
    const val SSID = "ssid"
    const val PASSWORD = "password"
    const val AUTH = "auth" // WPA | WEP | nopass
    const val HIDDEN = "hidden" // "true" | "false"
    const val EMAIL = "email"
    const val SUBJECT = "subject"
    const val PHONE = "phone"
    const val NAME = "name"

    /** The single most representative field of a type, used to carry content across type switches. */
    fun primaryKey(type: QrPayloadType): String = when (type) {
        QrPayloadType.TEXT -> TEXT
        QrPayloadType.LINK -> URL
        QrPayloadType.WIFI -> SSID
        QrPayloadType.EMAIL -> EMAIL
        QrPayloadType.PHONE -> PHONE
        QrPayloadType.CONTACT -> NAME
    }

    /**
     * Switches a barcode's QR payload type. Only Text and Link share their content (a URL typed as
     * text should stay); the value is MOVED between their two slots (the old one is cleared), so a
     * value the user cleared can never reappear from the other slot. All other types keep their own
     * independent fields. Leaving Link with just the auto-filled https:// drops it.
     */
    fun switchType(element: BarcodeElement, newType: QrPayloadType): BarcodeElement {
        val oldIsText = element.payloadType == QrPayloadType.TEXT || element.payloadType == QrPayloadType.LINK
        val newIsText = newType == QrPayloadType.TEXT || newType == QrPayloadType.LINK
        var fields = element.payload
        // Leaving Link with only the auto-filled https:// -> drop it so the stray default never carries.
        if (element.payloadType == QrPayloadType.LINK && fields[URL] == "https://") fields = fields - URL
        if (oldIsText && newIsText && element.payloadType != newType) {
            val oldKey = primaryKey(element.payloadType)
            val newKey = primaryKey(newType)
            val value = if (element.payloadType == QrPayloadType.TEXT) element.data else fields[oldKey].orEmpty()
            if (fields[newKey].isNullOrBlank() && value.isNotBlank()) fields = fields + (newKey to value)
            fields = fields - oldKey
        }
        return element.copy(payloadType = newType, payload = fields, data = build(newType, fields))
    }

    fun build(type: QrPayloadType, fields: Map<String, String>): String {
        fun f(key: String) = fields[key].orEmpty().trim()
        fun raw(key: String) = fields[key].orEmpty()
        return when (type) {
            QrPayloadType.TEXT -> fields[TEXT].orEmpty()
            QrPayloadType.LINK -> f(URL)
            QrPayloadType.WIFI -> {
                val auth = f(AUTH).ifBlank { "WPA" }
                val hidden = f(HIDDEN) == "true"
                // SSID and password are used verbatim (they may legitimately contain leading/trailing
                // spaces, and validation checks the untrimmed value); only auth/hidden are tokens.
                val password = if (auth == "nopass") "" else escWifi(raw(PASSWORD))
                "WIFI:T:$auth;S:${escWifi(raw(SSID))};P:$password;H:$hidden;;"
            }
            QrPayloadType.EMAIL -> {
                val subject = f(SUBJECT)
                if (subject.isBlank()) "mailto:${f(EMAIL)}"
                else "mailto:${f(EMAIL)}?subject=${urlEncode(subject)}"
            }
            QrPayloadType.PHONE -> "tel:${f(PHONE)}"
            QrPayloadType.CONTACT -> {
                val parts = buildList {
                    if (f(NAME).isNotBlank()) add("N:${escMeCard(f(NAME))}")
                    if (f(PHONE).isNotBlank()) add("TEL:${escMeCard(f(PHONE))}")
                    if (f(EMAIL).isNotBlank()) add("EMAIL:${escMeCard(f(EMAIL))}")
                }
                "MECARD:" + parts.joinToString(";") + ";;"
            }
        }
    }

    /**
     * Whether a WiFi password is plausible for the chosen encryption. WPA/WPA2/WPA3 passphrases are
     * 8-63 characters (or a 64-hex-digit PSK); WEP keys are 5 or 13 characters (or 10/26 hex).
     */
    fun isWifiPasswordValid(auth: String, password: String): Boolean = when (auth) {
        "nopass" -> true
        "WEP" -> password.length == 5 || password.length == 13 ||
            (password.length == 10 && isHex(password)) || (password.length == 26 && isHex(password))
        else -> password.length in 8..63 || (password.length == 64 && isHex(password))
    }

    private fun isHex(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    // WiFi QR spec: backslash-escape \ ; , : "
    private fun escWifi(s: String): String = s
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace(":", "\\:").replace("\"", "\\\"")

    // MeCard: backslash-escape \ ; : ,
    private fun escMeCard(s: String): String = s
        .replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:").replace(",", "\\,")

    private fun urlEncode(s: String): String = s.replace("%", "%25").replace(" ", "%20")
        .replace("&", "%26").replace("?", "%3F").replace("#", "%23")
}
