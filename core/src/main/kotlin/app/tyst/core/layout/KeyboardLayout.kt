package app.tyst.core.layout

enum class KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, SYMBOLS_MORE, LETTERS, EMOJI, SPACER }

/**
 * One key. [text] is what a CHAR key types; [label] is drawn (defaults to [text]).
 * [popup] lists long-press alternatives; the first is pre-selected.
 */
data class Key(
    val type: KeyType,
    val text: String = "",
    val label: String = text,
    val width: Float = 1f,
    val popup: List<String> = emptyList(),
    val hint: String = "",
) {
    val isSpecial: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE
}

data class KeyRow(val keys: List<Key>)

data class KeyboardLayout(val id: String, val rows: List<KeyRow>) {
    fun find(predicate: (Key) -> Boolean): Key? = rows.asSequence().flatMap { it.keys }.firstOrNull(predicate)
}

/** Popup entries that trigger an action instead of typing. */
object PopupAction {
    const val EMOJI = "\u0000emoji"
    const val SETTINGS = "\u0000settings"
    const val ONE_HANDED = "\u0000onehanded"
    fun isAction(s: String) = s.startsWith("\u0000")
}

object Layouts {
    private val ACCENTS = mapOf(
        'a' to listOf("å", "ä", "à", "á", "æ", "â", "ã"),
        'o' to listOf("ö", "ø", "ó", "ò", "ô", "õ"),
        'e' to listOf("é", "è", "ë", "ê"),
        'u' to listOf("ü", "ú", "ù", "û"),
        'i' to listOf("í", "ì", "ï", "î"),
        'c' to listOf("ç"),
        'n' to listOf("ñ"),
        's' to listOf("ß", "š"),
        'z' to listOf("ž"),
        'y' to listOf("ý", "ÿ"),
    )
    private const val TOP = "qwertyuiop"
    private const val DIGITS = "1234567890"

    private fun letter(c: Char, numberRow: Boolean): Key {
        val digit = TOP.indexOf(c).takeIf { it >= 0 && !numberRow }?.let { DIGITS[it].toString() }
        val popup = listOfNotNull(digit) + (ACCENTS[c] ?: emptyList())
        return Key(KeyType.CHAR, c.toString(), popup = popup, hint = digit ?: "")
    }

    fun letters(numberRow: Boolean, swedishRow: Boolean): KeyboardLayout {
        val rows = ArrayList<KeyRow>()
        if (numberRow) {
            val shifted = "!@#$%^&*()"
            rows += KeyRow(DIGITS.mapIndexed { i, c -> Key(KeyType.CHAR, c.toString(), popup = listOf(shifted[i].toString())) })
        }
        if (swedishRow) {
            // A compact centred row; the QWERTY keys below don't move.
            rows += KeyRow(listOf(Key(KeyType.SPACER, width = 3.5f)) + "åäö".map { Key(KeyType.CHAR, it.toString()) } + Key(KeyType.SPACER, width = 3.5f))
        }
        rows += KeyRow(TOP.map { letter(it, numberRow) })
        rows += KeyRow(listOf(Key(KeyType.SPACER, width = 0.5f)) + "asdfghjkl".map { letter(it, numberRow) } + Key(KeyType.SPACER, width = 0.5f))
        rows += KeyRow(listOf(Key(KeyType.SHIFT, width = 1.5f)) + "zxcvbnm".map { letter(it, numberRow) } + Key(KeyType.BACKSPACE, width = 1.5f))
        rows += bottomRow(KeyType.SYMBOLS, "?123")
        return KeyboardLayout(if (swedishRow) "letters_sv" else "letters", rows)
    }

    private fun bottomRow(switchType: KeyType, switchLabel: String) = KeyRow(
        listOf(
            Key(switchType, label = switchLabel, width = 1.5f),
            Key(KeyType.CHAR, ",", popup = listOf(PopupAction.EMOJI, PopupAction.SETTINGS, PopupAction.ONE_HANDED)),
            Key(KeyType.SPACE, " ", label = "", width = 5f),
            Key(KeyType.CHAR, ".", popup = listOf("?", "!", ",", ":", ";", "'", "\"", "-")),
            Key(KeyType.ENTER, width = 1.5f),
        ),
    )

    private fun chars(s: String, popups: Map<Char, List<String>> = emptyMap()) =
        s.map { Key(KeyType.CHAR, it.toString(), popup = popups[it] ?: emptyList()) }

    val symbols = KeyboardLayout(
        "symbols",
        listOf(
            KeyRow(chars("1234567890", mapOf('1' to listOf("¹", "½"), '2' to listOf("²"), '3' to listOf("³"), '0' to listOf("°")))),
            KeyRow(chars("@#€&_-+()/", mapOf('€' to listOf("$", "£", "¥", "kr"), '-' to listOf("–", "—"), '(' to listOf("[", "{", "<"), ')' to listOf("]", "}", ">")))),
            KeyRow(listOf(Key(KeyType.SYMBOLS_MORE, label = "=\\<", width = 1.5f)) + chars("*\"':;!?", mapOf('"' to listOf("“", "”", "«", "»"), '\'' to listOf("‘", "’"), '?' to listOf("¿"), '!' to listOf("¡"))) + Key(KeyType.BACKSPACE, width = 1.5f)),
            bottomRow(KeyType.LETTERS, "ABC"),
        ),
    )

    val symbolsMore = KeyboardLayout(
        "symbols_more",
        listOf(
            KeyRow(chars("~`|•√π÷×§∆")),
            KeyRow(chars("£$¥^°={}\\%")),
            KeyRow(listOf(Key(KeyType.SYMBOLS, label = "?123", width = 1.5f)) + chars("©®™✓[]<>") + Key(KeyType.BACKSPACE, width = 1.5f)),
            bottomRow(KeyType.LETTERS, "ABC"),
        ),
    )

    val numberPad = KeyboardLayout(
        "number",
        listOf(
            KeyRow(chars("123")),
            KeyRow(chars("456")),
            KeyRow(chars("789")),
            KeyRow(listOf(Key(KeyType.CHAR, ",", popup = listOf(".", "-")), Key(KeyType.CHAR, "0"), Key(KeyType.BACKSPACE))),
            KeyRow(listOf(Key(KeyType.LETTERS, label = "ABC"), Key(KeyType.SPACE, " ", label = ""), Key(KeyType.ENTER))),
        ),
    )

    val phonePad = KeyboardLayout(
        "phone",
        listOf(
            KeyRow(chars("123")),
            KeyRow(chars("456")),
            KeyRow(chars("789")),
            KeyRow(listOf(Key(KeyType.CHAR, "*", popup = listOf("#", ",", ";")), Key(KeyType.CHAR, "0", popup = listOf("+")), Key(KeyType.BACKSPACE))),
            KeyRow(listOf(Key(KeyType.CHAR, "+"), Key(KeyType.SPACE, " ", label = ""), Key(KeyType.ENTER))),
        ),
    )
}
