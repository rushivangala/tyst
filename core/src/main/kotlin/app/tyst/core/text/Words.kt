package app.tyst.core.text

import java.text.Normalizer

/** Small text helpers shared by prediction, correction and the typing controller. */
object Words {
    const val SENTENCE_START = "<s>"

    private val SENTENCE_END = charArrayOf('.', '!', '?', '\n')

    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '’'

    /** Lower-cases and folds the typographic apostrophe so lookups match the word lists. */
    fun normalize(word: String): String = word.replace('’', '\'').lowercase()

    /** The word the cursor is in, made of the word characters directly before the cursor. */
    fun currentWord(before: String): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        var word = before.substring(i)
        while (word.startsWith("'") || word.startsWith("’")) word = word.substring(1)
        return word
    }

    /** Word characters directly after the cursor (for a cursor placed inside a word). */
    fun wordAfter(after: String): String {
        var i = 0
        while (i < after.length && isWordChar(after[i])) i++
        return after.substring(0, i)
    }

    /**
     * Up to [n] normalized words before the current word, oldest first. A sentence boundary stops the
     * walk and is reported as [SENTENCE_START], so "Hi. Can we" gives ["<s>", "can", "we"].
     */
    fun previousWords(before: String, n: Int): List<String> {
        val cut = before.length - currentWord(before).length
        val text = before.substring(0, cut)
        val out = ArrayDeque<String>()
        var i = text.length
        while (out.size < n) {
            // Skip separators, watching for a sentence end.
            var sawEnd = false
            while (i > 0 && !isWordChar(text[i - 1])) {
                if (text[i - 1] in SENTENCE_END) sawEnd = true
                i--
            }
            if (sawEnd || i == 0) {
                out.addFirst(SENTENCE_START)
                break
            }
            val end = i
            while (i > 0 && isWordChar(text[i - 1])) i--
            out.addFirst(normalize(text.substring(i, end)))
        }
        return out.toList()
    }

    /** True when the next letter typed starts a sentence. */
    fun isSentenceStart(before: String): Boolean {
        val trimmed = before.trimEnd(' ', '\t')
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        if (last == '\n') return true
        // "Hi. " starts a new sentence; "Hi." does not until a space is typed.
        return last in SENTENCE_END && trimmed.length < before.length
    }

    /** Splits text into word tokens, keeping their original spelling. */
    fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            if (isWordChar(text[i])) {
                val start = i
                while (i < text.length && isWordChar(text[i])) i++
                out += text.substring(start, i).trim('\'', '’')
            } else {
                i++
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    /** Applies the capitalisation pattern of [template] to [word] without lowering [word]'s own capitals. */
    fun matchCase(template: String, word: String): String {
        if (template.isEmpty() || word.isEmpty()) return word
        val letters = template.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return word.uppercase()
        if (template.first().isUpperCase()) return word.replaceFirstChar { it.uppercaseChar() }
        return word
    }

    fun capitalize(word: String): String = word.replaceFirstChar { it.uppercaseChar() }

    fun stripDiacritics(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun hasSwedishLetters(s: String): Boolean = s.any { it in "åäöÅÄÖ" }

    /**
     * Length in chars of the last user-perceived character: emoji with skin tones, variation
     * selectors and ZWJ sequences, flags, keycaps and combining marks count as one. Done by hand
     * because java.text.BreakIterator handles emoji differently across JDK versions.
     */
    fun lastGraphemeLength(before: String): Int {
        if (before.isEmpty()) return 0
        var start = before.length
        fun prevCodePoint(at: Int): Int = Character.codePointBefore(before, at)
        fun isExtender(cp: Int) = cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 || cp in 0x1F3FB..0x1F3FF ||
            cp in 0xE0020..0xE007F || Character.getType(cp).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }
        fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF
        // The last code point, then any extenders before it belong to the same base.
        var cp = prevCodePoint(start)
        start -= Character.charCount(cp)
        while (true) {
            while (isExtender(cp) && start > 0) {
                cp = prevCodePoint(start)
                start -= Character.charCount(cp)
            }
            if (isRegional(cp) && start > 0 && isRegional(prevCodePoint(start))) {
                // A flag is a pair of regional indicators; count pairs back from the end.
                var n = 1
                var s = start
                while (s > 0 && isRegional(prevCodePoint(s))) { n++; s -= Character.charCount(prevCodePoint(s)) }
                if (n % 2 == 0) start -= Character.charCount(prevCodePoint(start))
            }
            if (start > 0 && prevCodePoint(start) == 0x200D && start - 1 > 0) {
                start -= 1
                cp = prevCodePoint(start)
                start -= Character.charCount(cp)
                continue
            }
            break
        }
        return before.length - start
    }
}
