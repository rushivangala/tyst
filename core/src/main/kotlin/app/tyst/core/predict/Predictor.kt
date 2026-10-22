package app.tyst.core.predict

import app.tyst.core.correct.Autocorrector
import app.tyst.core.correct.CorrectionOptions
import app.tyst.core.correct.CorrectionReason
import app.tyst.core.correct.TapOffset
import app.tyst.core.lexicon.Lexicon
import app.tyst.core.text.Words
import kotlin.math.ln

enum class SuggestionKind { COMPLETION, NEXT_WORD, CORRECTION, RESTORE, TYPED }

data class Suggestion(val text: String, val kind: SuggestionKind, val score: Double = 0.0)

/**
 * Ranked suggestions for the strip. [autoCorrection] is the word that space will put in place of the
 * current word, if any. The strip shows [suggestions][0] in the centre.
 */
data class Predictions(val suggestions: List<Suggestion>, val autoCorrection: String?) {
    companion object {
        val EMPTY = Predictions(emptyList(), null)
    }
}

class Predictor(
    private val lexicon: Lexicon,
    private val base: NGramModel,
    private val personal: PersonalModel,
    private val autocorrector: Autocorrector,
) {
    /** [taps]: where the finger landed for each letter of the current word, if known (see Autocorrector.check). */
    fun predict(before: String, options: CorrectionOptions, limit: Int = 3, taps: List<TapOffset?>? = null): Predictions {
        val current = Words.currentWord(before)
        val previous = Words.previousWords(before, 2)
        return if (current.isEmpty()) Predictions(nextWords(previous, limit), null)
        else completions(current, previous, options, limit, taps)
    }

    private fun nextWords(previous: List<String>, limit: Int): List<Suggestion> {
        val scores = HashMap<String, Double>()
        for ((w, p) in base.next(previous)) scores.merge(w, p, Double::plus)
        for ((w, p) in personal.ngrams.next(previous)) scores.merge(w, 2.5 * p, Double::plus)
        // A light prior keeps frequent words ahead among equal context scores.
        for (w in scores.keys.toList()) scores[w] = scores.getValue(w) + 0.02 * (lexicon.englishFreq(w) / 100.0) + 0.05 * ln(1.0 + personal.count(w))
        scores.remove(Words.SENTENCE_START)
        val sentenceStart = previous.lastOrNull() == Words.SENTENCE_START
        val ranked = scores.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        for (f in if (sentenceStart) STARTERS else FALLBACK) if (ranked.size < limit && f !in ranked) ranked += f
        return ranked.take(limit).map { w ->
            val shown = display(w)
            Suggestion(if (sentenceStart) Words.capitalize(shown) else shown, SuggestionKind.NEXT_WORD, scores[w] ?: 0.0)
        }
    }

    private fun completions(current: String, previous: List<String>, options: CorrectionOptions, limit: Int, taps: List<TapOffset?>?): Predictions {
        val key = Words.normalize(current)
        val context = HashMap(base.next(previous))
        for ((w, p) in personal.ngrams.next(previous)) context.merge(w, 2.5 * p, Double::plus)

        val scored = HashMap<String, Double>()
        fun add(w: String, freq: Int) {
            val s = freq / 100.0 + 4.0 * (context[w] ?: 0.0) + 0.8 * ln(1.0 + personal.count(w)) +
                (if (w == key) 1.0 else 0.0) - 0.05 * (w.length - key.length)
            if (s > (scored[w] ?: Double.NEGATIVE_INFINITY)) scored[w] = s
        }
        lexicon.completions(key, 40, options.swedishWords).forEach { (w, f) -> add(w, f) }
        for (lw in personal.dictionaryWords) {
            val n = Words.normalize(lw.word)
            if (n.startsWith(key)) add(n, 450)
        }
        for ((w, c) in personal.ngrams.unigrams) if (c >= 2 && w.startsWith(key) && w != Words.SENTENCE_START) add(w, 300 + 20 * minOf(c, 10))
        personal.blockedWords.forEach { scored.remove(it) }

        val check = autocorrector.check(current, previous, options, taps)
        val out = ArrayList<Suggestion>()
        when (check.reason) {
            CorrectionReason.RESTORE -> check.suggestions.forEach { out += Suggestion(it, SuggestionKind.RESTORE, 99.0) }
            CorrectionReason.MISSPELLING, CorrectionReason.TYPO, CorrectionReason.TOO_UNSURE -> {
                check.suggestions.forEach { out += Suggestion(it, SuggestionKind.CORRECTION, 98.0) }
            }
            else -> Unit
        }
        val known = check.reason in setOf(CorrectionReason.KNOWN_WORD, CorrectionReason.SWEDISH_WORD, CorrectionReason.PERSONAL_WORD)
        for ((w, s) in scored.entries.sortedByDescending { it.value }) {
            val kind = if (w == key) SuggestionKind.TYPED else SuggestionKind.COMPLETION
            out += Suggestion(Words.matchCase(current, display(w)), kind, s)
        }
        // The typed word stays reachable when it isn't known, so it can be kept as typed.
        if (!known) out += Suggestion(current, SuggestionKind.TYPED, -1.0)

        val unique = out.distinctBy { it.text.lowercase() }.toMutableList()
        // When the typed word is a real word, keep it in the centre ahead of longer completions.
        if (known) {
            val typedIdx = unique.indexOfFirst { it.kind == SuggestionKind.TYPED }
            if (typedIdx > 0) unique.add(0, unique.removeAt(typedIdx))
        }
        // With a correction pending, the word as typed sits next to it so it can be kept.
        if (check.autoApply != null) {
            val typedIdx = unique.indexOfFirst { it.text == current }
            if (typedIdx > 1) unique.add(1, unique.removeAt(typedIdx))
        }
        return Predictions(unique.take(limit), check.autoApply)
    }

    private fun display(w: String): String = personal.displayForm(w) ?: lexicon.display(w)

    companion object {
        private val STARTERS = listOf("I", "the", "Thanks")
        private val FALLBACK = listOf("the", "to", "and", "a", "I")
    }
}
