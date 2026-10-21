package app.tyst.core.write

import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.correct.Autocorrector
import app.tyst.core.correct.CorrectionOptions
import app.tyst.core.correct.CorrectionReason
import app.tyst.core.text.Words

enum class FixKind(val label: String) { SPELLING("Spelling"), GRAMMAR("Grammar"), PUNCTUATION("Punctuation"), CAPITALS("Capitals") }

/** One suggested fix over [start] until [end] of the original text. */
data class Fix(val start: Int, val end: Int, val original: String, val replacement: String, val kind: FixKind)

/**
 * The on-device proofread tool: spelling (with the Swedish guard), "i" → "I", sentence capitals,
 * repeated words, a/an, spacing around punctuation, and a few common confusions.
 */
class Proofreader(private val autocorrector: Autocorrector) {
    /** [startsSentence] is false for a selection that begins mid-sentence, so its first word keeps its case. */
    fun check(text: String, swedishWords: Boolean = true, startsSentence: Boolean = true): List<Fix> {
        val fixes = ArrayList<Fix>()
        spelling(text, swedishWords, fixes)
        regexFix(text, Regex("(?<![\\p{L}'])i(?![\\p{L}'’])"), FixKind.CAPITALS, fixes) { "I" }
        regexFix(text, Regex("(?<![\\p{L}'])i(['’](m|ll|ve|d))(?![\\p{L}])"), FixKind.CAPITALS, fixes) { "I" + it.groupValues[1] }
        repeated(text, fixes)
        articles(text, fixes)
        for ((pattern, replacement) in CONFUSIONS) regexFix(text, pattern, FixKind.GRAMMAR, fixes) { m -> Words.matchCase(m.value, replacement) }
        regexFix(text, THEN_THAN, FixKind.GRAMMAR, fixes) { it.groupValues[1] + " " + Words.matchCase(it.groupValues[2], "than") }
        regexFix(text, Regex("[ \\t]{2,}"), FixKind.PUNCTUATION, fixes) { " " }
        regexFix(text, Regex("[ \\t]+(?=[,.!?;:])"), FixKind.PUNCTUATION, fixes) { "" }
        regexFix(text, Regex("(?<=[\\p{L}])([,;])(?=[\\p{L}])"), FixKind.PUNCTUATION, fixes) { it.value + " " }
        capitals(text, fixes, startsSentence)
        return fixes.sortedWith(compareBy({ it.start }, { -it.end })).fold(ArrayList()) { acc, f ->
            if (acc.isEmpty() || f.start >= acc.last().end) acc += f
            acc
        }
    }

    /** Applies the accepted fixes; fixes must come from [check] on the same text. */
    fun apply(text: String, fixes: List<Fix>): String {
        val sb = StringBuilder(text)
        for (f in fixes.sortedByDescending { it.start }) sb.replace(f.start, f.end, f.replacement)
        return sb.toString()
    }

    private fun spelling(text: String, swedishWords: Boolean, out: MutableList<Fix>) {
        val opts = CorrectionOptions(AutocorrectStrength.STRONG, swedishWords, restoreDiacritics = swedishWords)
        val word = Regex("[\\p{L}][\\p{L}'’]*")
        for (m in word.findAll(text)) {
            val w = m.value
            if (w == "i") continue
            val before = text.substring(0, m.range.first)
            if (before.endsWith("@") || before.endsWith("/") || before.endsWith(".") && !before.endsWith(". ")) continue
            val prev = Words.previousWords(before + w, 2)
            val r = autocorrector.check(w, prev, opts)
            val replacement = when {
                r.autoApply != null -> r.autoApply
                r.reason == CorrectionReason.RESTORE -> r.suggestions.firstOrNull()
                else -> null
            }
            if (replacement != null && replacement != w) out += Fix(m.range.first, m.range.last + 1, w, replacement, FixKind.SPELLING)
        }
    }

    private fun repeated(text: String, out: MutableList<Fix>) {
        val r = Regex("(?i)\\b([\\p{L}]+)(\\s+)\\1\\b")
        for (m in r.findAll(text)) {
            if (m.groupValues[1].lowercase() in ALLOWED_REPEATS) continue
            out += Fix(m.range.first, m.range.last + 1, m.value, m.groupValues[1], FixKind.GRAMMAR)
        }
    }

    private fun articles(text: String, out: MutableList<Fix>) {
        val r = Regex("(?<![\\p{L}])(a|an|A|An)\\s+([\\p{L}]+)")
        for (m in r.findAll(text)) {
            val article = m.groupValues[1]
            val next = m.groupValues[2].lowercase()
            val wantsAn = startsWithVowelSound(next)
            val isAn = article.lowercase() == "an"
            if (wantsAn != isAn) {
                val fixed = if (wantsAn) "an" else "a"
                val start = m.range.first
                out += Fix(start, start + article.length, article, Words.matchCase(article, fixed), FixKind.GRAMMAR)
            }
        }
    }

    private fun startsWithVowelSound(w: String): Boolean {
        if (SILENT_H.any { w.startsWith(it) }) return true
        if (CONSONANT_SOUND.any { w.startsWith(it) }) return false
        if (w.length <= 1 || w.all { it.isUpperCase() }) return false
        return w.first() in "aeio" || (w.first() == 'u' && !w.startsWith("un") && w.getOrNull(2) !in listOf('i', 'e')) || w.startsWith("un")
    }

    private fun capitals(text: String, out: MutableList<Fix>, startsSentence: Boolean) {
        var start = startsSentence
        for (i in text.indices) {
            val c = text[i]
            if (start && c.isLetter()) {
                if (c.isLowerCase()) {
                    // Leave lower-case brand names and URLs alone.
                    val wordEnd = text.indexOfAny(charArrayOf(' ', '\n', ',', '.'), i).let { if (it < 0) text.length else it }
                    val word = text.substring(i, wordEnd)
                    if (!word.contains('@') && !word.contains("://") && !word.drop(1).any { it.isUpperCase() }) {
                        out += Fix(i, i + 1, c.toString(), c.uppercaseChar().toString(), FixKind.CAPITALS)
                    }
                }
                start = false
            } else if (c == '.' || c == '!' || c == '?') {
                // "e.g." and decimals don't end a sentence.
                start = i + 1 >= text.length || text[i + 1].isWhitespace()
            } else if (c == '\n') {
                start = true
            } else if (!c.isWhitespace() && c != '"' && c != '(' && c != '“') {
                start = false
            }
        }
    }

    private fun regexFix(text: String, r: Regex, kind: FixKind, out: MutableList<Fix>, replace: (MatchResult) -> String) {
        for (m in r.findAll(text)) {
            val rep = replace(m)
            if (rep != m.value) out += Fix(m.range.first, m.range.last + 1, m.value, rep, kind)
        }
    }

    companion object {
        private val ALLOWED_REPEATS = setOf("had", "that", "very", "bye", "ha", "no", "so", "really", "hej", "tack")
        private val SILENT_H = listOf("hour", "honest", "honor", "honour", "heir")
        private val CONSONANT_SOUND = listOf("uni", "use", "usu", "uti", "eur", "one", "once", "ubiq", "uk")
        private val CONFUSIONS = listOf(
            Regex("(?i)\\bcould of\\b") to "could have",
            Regex("(?i)\\bshould of\\b") to "should have",
            Regex("(?i)\\bwould of\\b") to "would have",
            Regex("(?i)\\byour welcome\\b") to "you're welcome",
            Regex("(?i)\\balot\\b") to "a lot",
        )
        val THEN_THAN = Regex("(?i)\\b(more|less|better|worse|rather|bigger|smaller|faster) (then)\\b")
    }
}
