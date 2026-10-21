package app.tyst.core.correct

import app.tyst.core.lexicon.Lexicon
import app.tyst.core.predict.NGramModel
import app.tyst.core.predict.PersonalModel
import app.tyst.core.text.Words
import kotlin.math.ln

enum class AutocorrectStrength { OFF, MILD, MEDIUM, STRONG }

data class CorrectionOptions(
    val strength: AutocorrectStrength = AutocorrectStrength.MEDIUM,
    val swedishWords: Boolean = true,
    val restoreDiacritics: Boolean = true,
)

enum class CorrectionReason { NONE, KNOWN_WORD, SWEDISH_WORD, PERSONAL_WORD, MISSPELLING, TYPO, RESTORE, TOO_UNSURE }

/**
 * Result of checking a finished word. [autoApply] is what space or punctuation will replace the word
 * with; [suggestions] are offered in the strip but never applied by themselves.
 */
data class CorrectionResult(
    val autoApply: String?,
    val suggestions: List<String>,
    val reason: CorrectionReason,
) {
    companion object {
        fun none(reason: CorrectionReason) = CorrectionResult(null, emptyList(), reason)
    }
}

class Autocorrector(
    private val lexicon: Lexicon,
    private val base: NGramModel,
    private val personal: PersonalModel,
) {
    data class Candidate(val word: String, val distance: Double, val score: Double)

    fun check(word: String, previous: List<String>, options: CorrectionOptions): CorrectionResult {
        if (word.isBlank() || word.any { it.isDigit() } || word.none { it.isLetter() }) {
            return CorrectionResult.none(CorrectionReason.NONE)
        }
        val key = Words.normalize(word)
        if (personal.isNeverCorrect(key)) return CorrectionResult.none(CorrectionReason.PERSONAL_WORD)
        if (personal.inDictionary(key)) return CorrectionResult.none(CorrectionReason.PERSONAL_WORD)

        lexicon.misspelling(key)?.let { m ->
            val fix = cased(word, m.correction)
            if (fix == word) return CorrectionResult.none(CorrectionReason.KNOWN_WORD)
            val apply = options.strength != AutocorrectStrength.OFF && (!m.contextual || contextWants(key, previous))
            return if (apply) CorrectionResult(fix, listOf(fix), CorrectionReason.MISSPELLING)
            else CorrectionResult(null, listOf(fix), CorrectionReason.MISSPELLING)
        }

        if (options.swedishWords) {
            if (options.restoreDiacritics) {
                lexicon.restoreDiacritics(key)?.let { restored ->
                    return CorrectionResult(null, listOf(cased(word, restored)), CorrectionReason.RESTORE)
                }
            }
            if (lexicon.isSwedish(key) && !lexicon.isEnglish(key)) return CorrectionResult.none(CorrectionReason.SWEDISH_WORD)
        }
        if (lexicon.isEnglish(key)) return CorrectionResult.none(CorrectionReason.KNOWN_WORD)
        if (personal.count(key) >= 3) return CorrectionResult.none(CorrectionReason.PERSONAL_WORD)

        val candidates = candidates(word, previous, options, limit = 3)
        if (candidates.isEmpty()) return CorrectionResult.none(CorrectionReason.TOO_UNSURE)
        val suggestions = candidates.map { cased(word, display(it.word)) }
        val best = candidates.first()
        val second = candidates.getOrNull(1)
        val rule = rules.getValue(options.strength)
        // A capital mid-sentence is probably a name we don't know; never change it by itself.
        val looksLikeName = word.first().isUpperCase() && previous.lastOrNull() != Words.SENTENCE_START
        val confident = rule != null && !looksLikeName && key.length >= 3 &&
            best.distance <= (if (key.length >= 6) rule.maxDistanceLong else rule.maxDistance) &&
            freqOf(best.word) >= rule.minFreq &&
            (second == null || best.score - second.score >= rule.margin)
        return CorrectionResult(if (confident) suggestions.first() else null, suggestions, if (confident) CorrectionReason.TYPO else CorrectionReason.TOO_UNSURE)
    }

    /** Spelling candidates for an unknown word, best first. */
    fun candidates(word: String, previous: List<String>, options: CorrectionOptions, limit: Int): List<Candidate> {
        val key = Words.normalize(word)
        if (key.length < 2) return emptyList()
        val maxDistance = if (key.length >= 6) 2.0 else 1.5
        val first = buildSet {
            add(KeyProximity.base(key[0]))
            addAll(KeyProximity.neighbours(KeyProximity.base(key[0])))
            add(key[1])
            if (key[0] == 'a' || key[0] == 'o') addAll(listOf('å', 'ä', 'ö'))
        }
        val context = context(previous)
        val found = ArrayList<Candidate>()
        val plainKey = Words.stripDiacritics(key)
        fun consider(w: String, freq: Int) {
            // With "Restore å ä ö" off, a diacritic-only difference is not offered either.
            if (!options.restoreDiacritics && w != key && Words.stripDiacritics(w) == plainKey) return
            val d = typoDistance(w, key, maxDistance)
            if (d > maxDistance) return
            found += Candidate(w, d, score(w, freq, d, context))
        }
        lexicon.forEachCandidate(first, key.length - 2, key.length + 2, options.swedishWords) { w, f -> consider(w, f) }
        for (lw in personal.dictionaryWords) {
            val n = Words.normalize(lw.word)
            if (n != key && kotlin.math.abs(n.length - key.length) <= 2) consider(n, 400)
        }
        return found.sortedByDescending { it.score }.distinctBy { it.word }.take(limit)
    }

    private fun score(w: String, freq: Int, distance: Double, context: Map<String, Double>): Double =
        freq / 100.0 - 3.0 * distance + 3.0 * (context[w] ?: 0.0) + 0.6 * ln(1.0 + personal.count(w))

    private fun context(previous: List<String>): Map<String, Double> {
        val out = HashMap(base.next(previous))
        for ((w, p) in personal.ngrams.next(previous)) out.merge(w, 2 * p, Double::plus)
        return out
    }

    private fun freqOf(w: String): Int = maxOf(lexicon.englishFreq(w), lexicon.swedishFreq(w), if (personal.inDictionary(w)) 400 else 0)

    private fun display(w: String): String = personal.displayForm(w) ?: lexicon.display(w)

    private fun contextWants(key: String, previous: List<String>): Boolean {
        val prev = previous.lastOrNull() ?: Words.SENTENCE_START
        return when (key) {
            "wont", "cant" -> prev in PRONOUNS
            "lets", "ill", "id" -> prev == Words.SENTENCE_START
            else -> true
        }
    }

    /** Keeps the replacement's own capitals (I'm, Västerås) and copies the typed word's otherwise. */
    private fun cased(typed: String, replacement: String): String =
        if (replacement.any { it.isUpperCase() } && !typed.all { !it.isLetter() || it.isUpperCase() }) replacement
        else Words.matchCase(typed, replacement)

    private data class Rule(val maxDistance: Double, val maxDistanceLong: Double, val minFreq: Int, val margin: Double)

    private val rules: Map<AutocorrectStrength, Rule?> = mapOf(
        AutocorrectStrength.OFF to null,
        AutocorrectStrength.MILD to Rule(0.7, 1.0, 380, 1.2),
        AutocorrectStrength.MEDIUM to Rule(1.0, 1.6, 300, 0.6),
        AutocorrectStrength.STRONG to Rule(1.3, 2.0, 250, 0.3),
    )

    companion object {
        private val PRONOUNS = setOf("i", "you", "we", "they", "he", "she", "it", "that", "this", "there", "who", "people", "someone")
    }
}
