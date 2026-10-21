package app.tyst.core.predict

import app.tyst.core.text.Words

/** Unigram, bigram and trigram counts over normalized tokens. Not thread-safe. */
class NGramModel {
    val unigrams = HashMap<String, Int>()
    val bigrams = HashMap<String, HashMap<String, Int>>()
    val trigrams = HashMap<String, HashMap<String, Int>>()

    /** Adds one sentence of normalized tokens; a sentence start marker is prepended. */
    fun addSentence(tokens: List<String>, weight: Int = 1) {
        if (tokens.isEmpty()) return
        val seq = listOf(Words.SENTENCE_START) + tokens
        for (i in 1 until seq.size) {
            val w = seq[i]
            unigrams.merge(w, weight, Int::plus)
            bigrams.getOrPut(seq[i - 1]) { HashMap() }.merge(w, weight, Int::plus)
            if (i >= 2) trigrams.getOrPut(seq[i - 2] + " " + seq[i - 1]) { HashMap() }.merge(w, weight, Int::plus)
        }
    }

    fun addBigram(prev: String, next: String, weight: Int = 1) {
        bigrams.getOrPut(prev) { HashMap() }.merge(next, weight, Int::plus)
    }

    fun addTrigram(p1: String, p2: String, next: String, weight: Int = 1) {
        trigrams.getOrPut("$p1 $p2") { HashMap() }.merge(next, weight, Int::plus)
    }

    fun next(prev: List<String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        if (prev.isEmpty()) return out
        val p2 = prev.last()
        bigrams[p2]?.let { m ->
            val total = m.values.sum().toDouble()
            for ((w, c) in m) out.merge(w, c / total, Double::plus)
        }
        if (prev.size >= 2) {
            trigrams[prev[prev.size - 2] + " " + p2]?.let { m ->
                val total = m.values.sum().toDouble()
                for ((w, c) in m) out.merge(w, 2.0 * c / total, Double::plus)
            }
        }
        return out
    }

    fun remove(word: String) {
        unigrams.remove(word)
        bigrams.remove(word)
        bigrams.values.forEach { it.remove(word) }
        trigrams.keys.removeAll { key -> key.split(' ').contains(word) }
        trigrams.values.forEach { it.remove(word) }
        bigrams.values.removeAll { it.isEmpty() }
        trigrams.values.removeAll { it.isEmpty() }
    }

    fun clear() {
        unigrams.clear(); bigrams.clear(); trigrams.clear()
    }

    /** Keeps the model bounded: drops the rarest entries when it grows past [maxEntries]. */
    fun prune(maxEntries: Int) {
        fun prune(map: HashMap<String, HashMap<String, Int>>) {
            var size = map.values.sumOf { it.size }
            var floor = 1
            while (size > maxEntries && floor < 1000) {
                map.values.forEach { inner -> inner.values.removeAll { it <= floor } }
                map.values.removeAll { it.isEmpty() }
                size = map.values.sumOf { it.size }
                floor++
            }
        }
        prune(bigrams)
        prune(trigrams)
        if (unigrams.size > maxEntries) {
            val keep = unigrams.entries.sortedByDescending { it.value }.take(maxEntries).map { it.key }.toHashSet()
            unigrams.keys.retainAll(keep)
        }
    }

    companion object {
        /** Builds the base model from the bundled phrase list. */
        fun loadBase(): NGramModel {
            val model = NGramModel()
            val stream = NGramModel::class.java.getResourceAsStream("/tyst/phrases.txt") ?: return model
            stream.bufferedReader(Charsets.UTF_8).readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line -> sentences(line).forEach { model.addSentence(it) } }
            return model
        }

        /** Splits text into sentences of normalized tokens. */
        fun sentences(text: String): List<List<String>> =
            text.split(Regex("[.!?\\n]+"))
                .map { s -> Words.tokens(s).map(Words::normalize) }
                .filter { it.isNotEmpty() }
    }
}
