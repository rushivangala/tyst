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

/**
 * The last text the user copied, offered once as a paste chip. Held in memory only.
 * [sensitive] clips (Android's IS_SENSITIVE flag, e.g. from a password manager) are shown masked.
 */
data class CopiedText(val text: String, val copiedAtMillis: Long, val sensitive: Boolean = false) {
    fun isFresh(nowMillis: Long): Boolean = nowMillis >= copiedAtMillis && nowMillis - copiedAtMillis < WINDOW_MS

    /** One line for the strip: whitespace folded, cut to [max] characters, or dots when sensitive. */
    fun preview(max: Int = 24): String {
        if (sensitive) return "••••••"
        val flat = text.trim().replace(Regex("\\s+"), " ")
        return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
    }

    companion object {
        /** How long a copied item is offered: 3 minutes, like copied codes at most. */
        const val WINDOW_MS = 3 * 60 * 1000L
        /** Longer clips can still be pasted with Android's own paste; the chip skips them. */
        const val MAX_CHARS = 5_000
    }
}
