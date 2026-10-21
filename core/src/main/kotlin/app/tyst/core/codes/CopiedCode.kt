package app.tyst.core.codes

/** Finds a verification code in copied text. Codes are held in memory only and never stored. */
object OtpDetector {
    private val ONLY_CODE = Regex("^[0-9][0-9 \\-]{2,10}[0-9]$")
    private val KEYWORD = Regex("(?i)(code|kod|otp|one[- ]time|verification|verify|pin|passcode|2fa|login|sign[- ]in)")
    private val NUMBER = Regex("(?<![0-9+\\-])(?:G-)?([0-9]{3}[ \\-]?[0-9]{3}|[0-9]{4,8})(?![0-9])")

    fun extract(text: String?): String? {
        val t = text?.trim() ?: return null
        if (t.isEmpty() || t.length > 400) return null
        if (ONLY_CODE.matches(t)) {
            val digits = t.filter { it.isDigit() }
            return digits.takeIf { it.length in 4..8 }
        }
        if (!KEYWORD.containsMatchIn(t)) return null
        val found = NUMBER.findAll(t)
            .map { m -> m.groupValues[1].filter { it.isDigit() } }
            .filter { it.length in 4..8 }
            .distinct()
            .toList()
        return found.singleOrNull()
    }

    /** "482913" → "482 913"; four-digit codes stay as they are. */
    fun grouped(code: String): String = when (code.length) {
        6 -> code.substring(0, 3) + " " + code.substring(3)
        8 -> code.substring(0, 4) + " " + code.substring(4)
        else -> code
    }
}

/** A code the user just copied. It shows as a chip until [expirySeconds] have passed. */
data class CopiedCode(val code: String, val copiedAtMillis: Long) {
    fun remainingSeconds(nowMillis: Long, expirySeconds: Int): Int =
        maxOf(0, expirySeconds - ((nowMillis - copiedAtMillis) / 1000).toInt())

    fun isFresh(nowMillis: Long, expirySeconds: Int): Boolean =
        nowMillis >= copiedAtMillis && remainingSeconds(nowMillis, expirySeconds) > 0
}
