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

    /** See [DISTANCE_WEIGHT]; adjustable for tuning with the typing benchmark. */
    var distanceWeight = DISTANCE_WEIGHT

    /**
     * [taps], when given, are where the finger landed for each letter of [word] (null where unknown);
     * they make slips toward the key that was nearly hit cheaper.
     */
    fun check(word: String, previous: List<String>, options: CorrectionOptions, taps: List<TapOffset?>? = null): CorrectionResult {
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
            if (lexicon.isProtectedSwedish(key)) return CorrectionResult.none(CorrectionReason.SWEDISH_WORD)
        }
        if (personal.count(key) >= 3) return CorrectionResult.none(CorrectionReason.PERSONAL_WORD)

        val englishFreq = lexicon.englishFreq(key)
        val swedishFreq = if (options.swedishWords) lexicon.swedishFreq(key) else 0
        val rule = rules.getValue(options.strength)
        // A capital mid-sentence is probably a name we don't know; never change it by itself.
        val looksLikeName = word.first().isUpperCase() && previous.lastOrNull() != Words.SENTENCE_START
        if (englishFreq > 0 || swedishFreq > 0) {
            return checkKnown(word, key, englishFreq, swedishFreq, previous, options, rule, looksLikeName, taps)
        }

        val candidates = candidates(word, previous, options, limit = 3, taps)
        if (candidates.isEmpty()) return CorrectionResult.none(CorrectionReason.TOO_UNSURE)
        val suggestions = candidates.map { cased(word, display(it.word)) }
        val best = candidates.first()
        val second = candidates.getOrNull(1)
        val minLength = if (best.distance <= SINGLE_SLIP) 2 else 3
        // A capital is a name only if the fix isn't itself always capitalised (Momday → Monday).
        val nameGuard = looksLikeName && display(best.word).firstOrNull()?.isUpperCase() != true
        val confident = rule != null && !nameGuard && key.length >= minLength &&
            best.distance <= (if (key.length >= 6) rule.maxDistanceLong else rule.maxDistance) &&
            freqOf(best.word) >= rule.minFreq &&
            (second == null || best.score - second.score >= rule.margin)
        return CorrectionResult(if (confident) suggestions.first() else null, suggestions, if (confident) CorrectionReason.TYPO else CorrectionReason.TOO_UNSURE)
    }

    /**
     * A word the lexicon knows. Common words are always left alone. A rare one (often web noise like
     * "thr", or a Swedish word that is also an English slip, like "fot") is changed only when a much
     * likelier word is one key-slip away: the typed word, taken as meant, must lose to that word,
     * taken as a typo, by [RARE_MARGIN] (log10 units, so ~60×).
     */
    private fun checkKnown(
        word: String, key: String, englishFreq: Int, swedishFreq: Int, previous: List<String>,
        options: CorrectionOptions, rule: Rule?, looksLikeName: Boolean, taps: List<TapOffset?>?,
    ): CorrectionResult {
        val reason = if (englishFreq > 0) CorrectionReason.KNOWN_WORD else CorrectionReason.SWEDISH_WORD
        if (englishFreq >= TRUSTED_FREQ || rule == null || looksLikeName || key.length < 2) return CorrectionResult.none(reason)
        // Mild keeps every Swedish word as typed.
        if (swedishFreq > 0 && options.strength == AutocorrectStrength.MILD) return CorrectionResult.none(reason)
        val swedishContext = swedishContext(previous)
        val asSwedish = if (swedishFreq > 0) swedishFreq - (if (swedishContext) 0 else SWEDISH_IN_ENGLISH_PENALTY) else 0
        val asEnglish = if (lexicon.isUnlisted(key)) englishFreq - UNLISTED_PENALTY else englishFreq
        val selfFreq = maxOf(asEnglish, asSwedish)
        if (selfFreq >= TRUSTED_FREQ) return CorrectionResult.none(reason)

        val context = context(previous)
        val selfScore = selfFreq / 100.0 + 3.0 * (context[key] ?: 0.0) + 0.6 * ln(1.0 + personal.count(key))
        val candidates = candidates(word, previous, options, limit = 3, taps).filter { it.word != key }
        val best = candidates.firstOrNull() ?: return CorrectionResult.none(reason)
        val second = candidates.getOrNull(1)
        val suggestions = candidates.map { cased(word, display(it.word)) }
        val confident = best.distance <= SINGLE_SLIP &&
            freqOf(best.word) >= rule.minFreq &&
            best.score - selfScore >= RARE_MARGIN &&
            (second == null || best.score - second.score >= rule.margin)
        return if (confident) CorrectionResult(suggestions.first(), suggestions, CorrectionReason.TYPO)
        else CorrectionResult(null, suggestions, reason)
    }

    /** Spelling candidates for an unknown word, best first. */
    fun candidates(word: String, previous: List<String>, options: CorrectionOptions, limit: Int, taps: List<TapOffset?>? = null): List<Candidate> {
        val key = Words.normalize(word)
        val sub = if (taps != null && taps.size == key.length && taps.any { it != null }) {
            SubstitutionCost { intended, j -> KeyProximity.tapCost(intended, key[j], taps[j], distanceWeight) }
        } else null
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
        val englishText = !swedishContext(previous) && !Words.hasSwedishLetters(key)
        fun consider(w: String, freq: Int, d: Double) {
            // With "Restore å ä ö" off, a diacritic-only difference is not offered either.
            if (!options.restoreDiacritics && w != key && Words.stripDiacritics(w) == plainKey) return
            if (d > maxDistance) return
            var f = freq
            // In English text a Swedish word is an unlikely target for a slip ("xar" is "car", not "där");
            // names and places are the exception, they belong in English text too.
            if (englishText && Words.hasSwedishLetters(w) && lexicon.isSwedishOnly(w) && !lexicon.hasDisplayForm(w)) f -= SWEDISH_IN_ENGLISH_PENALTY
            if (lexicon.isUnlisted(w)) f -= UNLISTED_PENALTY
            found += Candidate(w, d, score(w, f, d, context))
        }
        lexicon.forEachWithin(key, maxDistance, first, key.length - 2, key.length + 2, options.swedishWords, sub) { w, f, d -> consider(w, f, d) }
        for (lw in personal.dictionaryWords) {
            val n = Words.normalize(lw.word)
            if (n != key && kotlin.math.abs(n.length - key.length) <= 2) consider(n, 400, typoDistance(n, key, maxDistance, sub))
        }
        return found.sortedByDescending { it.score }.distinctBy { it.word }.take(limit)
    }

    /** True when the words before look Swedish, so a Swedish word is as likely as an English one. */
    private fun swedishContext(previous: List<String>): Boolean =
        previous.any { it != Words.SENTENCE_START && lexicon.isSwedishOnly(it) && !lexicon.hasDisplayForm(it) }

    private fun score(w: String, freq: Int, distance: Double, context: Map<String, Double>): Double =
        freq / 100.0 - distanceWeight * distance + 3.0 * (context[w] ?: 0.0) + 0.6 * ln(1.0 + personal.count(w))

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
        /** English words at least this frequent (Zipf × 100; the 10,000 most common) are never changed. */
        const val TRUSTED_FREQ = 377
        /** A Swedish word inside English text is about 30× less likely than in Swedish text. */
        const val SWEDISH_IN_ENGLISH_PENALTY = 150
        /** How much likelier the replacement must be than a rare typed word, in log10 units. */
        const val RARE_MARGIN = 1.8
        /** A word no spell-checker lists is about 30× less likely to be meant. */
        const val UNLISTED_PENALTY = 150
        /** Log10 cost per unit of typo distance in candidate scores. */
        const val DISTANCE_WEIGHT = 4.0
        /** One neighbouring-key slip, a swap or a doubled letter. */
        const val SINGLE_SLIP = 0.7
        private val PRONOUNS = setOf("i", "you", "we", "they", "he", "she", "it", "that", "this", "there", "who", "people", "someone")
    }
}
