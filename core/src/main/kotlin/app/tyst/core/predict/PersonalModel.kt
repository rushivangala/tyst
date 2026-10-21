package app.tyst.core.predict

import app.tyst.core.text.Words

/** A word the user taught Tyst: typed often, or kept after undoing an autocorrect. */
data class LearnedWord(val word: String, val sourceApp: String, val learnedAt: Long, val uses: Int)

/**
 * Everything the keyboard learns about the user's words. Lives only on the phone; the app layer
 * stores [encode] output encrypted.
 */
class PersonalModel {
    val ngrams = NGramModel()
    private val dictionary = LinkedHashMap<String, LearnedWord>()
    private val pending = HashMap<String, Int>()
    private val restores = HashMap<String, Int>()
    private val blocked = HashSet<String>()
    private val neverCorrect = LinkedHashSet<String>()
    private val emoji = HashMap<String, Int>()

    val dictionaryWords: List<LearnedWord> get() = dictionary.values.toList()
    val blockedWords: Set<String> get() = blocked
    val neverCorrectWords: Set<String> get() = neverCorrect
    val emojiUsage: Map<String, Int> get() = emoji

    fun inDictionary(word: String): Boolean = Words.normalize(word) in dictionary
    fun isBlocked(word: String): Boolean = Words.normalize(word) in blocked
    fun isNeverCorrect(word: String): Boolean = Words.normalize(word) in neverCorrect.map(Words::normalize)

    /** Written form of a learned word, keeping the capitals the user typed ("Alma"). */
    fun displayForm(word: String): String? = dictionary[Words.normalize(word)]?.word

    fun count(word: String): Int = ngrams.unigrams[Words.normalize(word)] ?: 0

    /**
     * Records a committed word. Unknown words join the personal dictionary after [LEARN_AFTER_USES]
     * uses so a one-off typo is not learned.
     */
    fun onWordCommitted(word: String, previous: List<String>, known: Boolean, app: String, now: Long) {
        val key = Words.normalize(word)
        if (key.isEmpty() || key in blocked) return
        ngrams.unigrams.merge(key, 1, Int::plus)
        if (previous.isNotEmpty()) {
            ngrams.addBigram(previous.last(), key)
            if (previous.size >= 2) ngrams.addTrigram(previous[previous.size - 2], previous.last(), key)
        }
        if (!known && key !in dictionary && key.any { it.isLetter() } && key.length > 1) {
            val uses = (pending[key] ?: 0) + 1
            if (uses >= LEARN_AFTER_USES) {
                pending.remove(key)
                dictionary[key] = LearnedWord(word, app, now, uses)
            } else {
                pending[key] = uses
            }
        } else if (key in dictionary) {
            val w = dictionary.getValue(key)
            dictionary[key] = w.copy(uses = w.uses + 1)
        }
        if (ngrams.unigrams.size > MAX_ENTRIES * 2) ngrams.prune(MAX_ENTRIES)
    }

    /** The user undid an autocorrect of [original]; the second time, the word is learned. */
    fun onCorrectionUndone(original: String, app: String, now: Long): Boolean {
        val key = Words.normalize(original)
        val n = (restores[key] ?: 0) + 1
        restores[key] = n
        if (n >= LEARN_AFTER_RESTORES && key !in dictionary) {
            dictionary[key] = LearnedWord(original, app, now, n)
            return true
        }
        return false
    }

    fun addWord(word: String, app: String, now: Long) {
        val key = Words.normalize(word)
        blocked.remove(key)
        dictionary[key] = dictionary[key] ?: LearnedWord(word.trim(), app, now, 1)
    }

    /** Removes a word and everything learned about it; [block] stops it being learned again. */
    fun removeWord(word: String, block: Boolean) {
        val key = Words.normalize(word)
        dictionary.remove(key)
        pending.remove(key)
        restores.remove(key)
        ngrams.remove(key)
        if (block) blocked += key
    }

    fun unblock(word: String) {
        blocked.remove(Words.normalize(word))
    }

    fun addNeverCorrect(word: String) {
        if (word.isNotBlank()) neverCorrect += word.trim()
    }

    fun removeNeverCorrect(word: String) {
        neverCorrect.removeAll { Words.normalize(it) == Words.normalize(word) }
    }

    fun onEmojiUsed(e: String) {
        emoji.merge(e, 1, Int::plus)
    }

    fun clear() {
        ngrams.clear(); dictionary.clear(); pending.clear(); restores.clear(); blocked.clear(); neverCorrect.clear(); emoji.clear()
    }

    fun encode(): String = buildString {
        appendLine("V\t1")
        for ((w, c) in ngrams.unigrams) appendLine("U\t$w\t$c")
        for ((p, m) in ngrams.bigrams) for ((w, c) in m) appendLine("B\t$p\t$w\t$c")
        for ((p, m) in ngrams.trigrams) for ((w, c) in m) appendLine("T\t$p\t$w\t$c")
        for (d in dictionary.values) appendLine("D\t${esc(d.word)}\t${esc(d.sourceApp)}\t${d.learnedAt}\t${d.uses}")
        for ((w, c) in pending) appendLine("P\t$w\t$c")
        for ((w, c) in restores) appendLine("R\t$w\t$c")
        for (w in blocked) appendLine("X\t$w")
        for (w in neverCorrect) appendLine("N\t${esc(w)}")
        for ((e, c) in emoji) appendLine("E\t$e\t$c")
    }

    companion object {
        const val LEARN_AFTER_USES = 2
        const val LEARN_AFTER_RESTORES = 2
        const val MAX_ENTRIES = 20_000

        private fun esc(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        fun decode(text: String): PersonalModel {
            val m = PersonalModel()
            for (line in text.lineSequence()) {
                val p = line.split('\t')
                try {
                    when (p[0]) {
                        "U" -> m.ngrams.unigrams[p[1]] = p[2].toInt()
                        "B" -> m.ngrams.addBigram(p[1], p[2], p[3].toInt())
                        "T" -> m.ngrams.trigrams.getOrPut(p[1]) { HashMap() }[p[2]] = p[3].toInt()
                        "D" -> m.dictionary[Words.normalize(p[1])] = LearnedWord(p[1], p[2], p[3].toLong(), p[4].toInt())
                        "P" -> m.pending[p[1]] = p[2].toInt()
                        "R" -> m.restores[p[1]] = p[2].toInt()
                        "X" -> m.blocked += p[1]
                        "N" -> m.neverCorrect += p[1]
                        "E" -> m.emoji[p[1]] = p[2].toInt()
                    }
                } catch (_: RuntimeException) {
                    // Skip a damaged line rather than losing everything learned.
                }
            }
            return m
        }
    }
}
