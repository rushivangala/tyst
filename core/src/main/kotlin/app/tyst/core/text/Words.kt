package app.tyst.core.text

import java.text.BreakIterator
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

    /** Length in chars of the last user-perceived character (emoji sequences, combining marks). */
    fun lastGraphemeLength(before: String): Int {
        if (before.isEmpty()) return 0
        val tail = before.takeLast(32)
        val it = BreakIterator.getCharacterInstance()
        it.setText(tail)
        val end = it.last()
        val start = it.previous()
        var len = if (start == BreakIterator.DONE) tail.length else end - start
        // BreakIterator on older JDKs splits ZWJ emoji sequences; keep joining over U+200D.
        var s = tail.length - len
        while (s >= 2 && tail[s - 1] == '‍') {
            val prev = tail.substring(0, s - 1)
            val p = BreakIterator.getCharacterInstance().apply { setText(prev) }
            val pe = p.last()
            val ps = p.previous()
            val add = if (ps == BreakIterator.DONE) prev.length else pe - ps
            len += 1 + add
            s = tail.length - len
        }
        return len
    }
}
