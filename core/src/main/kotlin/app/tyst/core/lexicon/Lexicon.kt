package app.tyst.core.lexicon

import app.tyst.core.text.Words

/** A known misspelling. [contextual] ones are also rare real words and are fixed only in context. */
data class Misspelling(val correction: String, val contextual: Boolean)

/**
 * The built-in word knowledge: English (main language), Swedish words, names and places
 * (a recognition layer, never a second keyboard language) and a list of common misspellings.
 *
 * Frequencies are Zipf values × 100 (about 100 = rare, 700+ = "the").
 */
class Lexicon(
    english: Map<String, Int>,
    swedish: Map<String, Int>,
    private val misspellings: Map<String, Misspelling>,
    properEnglish: Collection<String>,
    properSwedish: Collection<String>,
    swedishLoanWords: Collection<String>,
) {
    private val en: Map<String, Int> = english
    private val sv: Map<String, Int>
    private val display = HashMap<String, String>()
    private val restore = HashMap<String, String>()
    private val sorted: Array<String>
    private val sortedFreq: IntArray
    private val swedishOnly = HashSet<String>()

    init {
        for (p in properEnglish) display[Words.normalize(p)] = p
        val svAll = HashMap(swedish)
        for (p in properSwedish) {
            val key = Words.normalize(p)
            display[key] = p
            svAll[key] = maxOf(svAll[key] ?: 0, PROPER_SWEDISH_FREQ)
        }
        for (w in swedishLoanWords) {
            val key = Words.normalize(w)
            svAll[key] = maxOf(svAll[key] ?: 0, PROPER_SWEDISH_FREQ)
        }
        misspellings.keys.forEach { svAll.remove(it) }
        sv = svAll

        for ((w, f) in sv) {
            if (!Words.hasSwedishLetters(w)) continue
            val plain = Words.stripDiacritics(w)
            if (plain == w || (en[plain] ?: 0) >= RESTORE_BLOCKING_ENGLISH_FREQ) continue
            val current = restore[plain]
            if (current == null || f > (sv[current] ?: 0)) restore[plain] = w
        }

        val merged = HashMap<String, Int>(en.size + sv.size)
        for ((w, f) in en) merged[w] = f
        for ((w, f) in sv) {
            if (w !in en) swedishOnly += w
            // Swedish words rank below English ones when completing a prefix unless they carry å ä ö
            // or are names and places, which an English word can't be confused with.
            val weighted = if (Words.hasSwedishLetters(w) || display.containsKey(w)) f else f - SWEDISH_PENALTY
            merged[w] = maxOf(merged[w] ?: 0, weighted)
        }
        val keys = merged.keys.toTypedArray()
        keys.sort()
        sorted = keys
        sortedFreq = IntArray(keys.size) { merged.getValue(keys[it]) }
    }

    val englishSize: Int get() = en.size
    val swedishSize: Int get() = sv.size

    fun englishFreq(word: String): Int = en[Words.normalize(word)] ?: 0
    fun swedishFreq(word: String): Int = sv[Words.normalize(word)] ?: 0
    fun isEnglish(word: String): Boolean = Words.normalize(word) in en
    fun isSwedish(word: String): Boolean = Words.normalize(word) in sv

    /** True for words only the Swedish layer knows (fika, Liseberg, Västerås). */
    fun isSwedishOnly(word: String): Boolean = Words.normalize(word) in swedishOnly

    fun misspelling(word: String): Misspelling? = misspellings[Words.normalize(word)]

    /** The written form of a word: "västerås" → "Västerås", "monday" → "Monday". */
    fun display(word: String): String = display[Words.normalize(word)] ?: word

    fun hasDisplayForm(word: String): Boolean = display.containsKey(Words.normalize(word))

    /** "vasteras" → "Västerås" when the plain spelling is not itself an English word. */
    fun restoreDiacritics(word: String): String? {
        val key = Words.normalize(word)
        if (Words.hasSwedishLetters(key)) return null
        return restore[key]?.let { display(it) }
    }

    /** Words starting with [prefix] (normalized), most frequent first. */
    fun completions(prefix: String, limit: Int, includeSwedish: Boolean = true): List<Pair<String, Int>> {
        val p = Words.normalize(prefix)
        if (p.isEmpty()) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < p) lo = mid + 1 else hi = mid
        }
        val found = ArrayList<Pair<String, Int>>()
        var i = lo
        while (i < sorted.size && sorted[i].startsWith(p)) {
            val w = sorted[i]
            if (includeSwedish || w !in swedishOnly) found += w to sortedFreq[i]
            i++
        }
        found.sortByDescending { it.second }
        return found.take(limit)
    }

    /** Iterates all words with the given first letter and a length within [minLen]..[maxLen]. */
    fun forEachCandidate(first: Set<Char>, minLen: Int, maxLen: Int, includeSwedish: Boolean, block: (String, Int) -> Unit) {
        for (c in first) {
            val start = c.toString()
            var lo = 0
            var hi = sorted.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid] < start) lo = mid + 1 else hi = mid
            }
            var i = lo
            while (i < sorted.size && sorted[i].isNotEmpty() && sorted[i][0] == c) {
                val w = sorted[i]
                if (w.length in minLen..maxLen && (includeSwedish || w !in swedishOnly)) block(w, sortedFreq[i])
                i++
            }
        }
    }

    companion object {
        const val PROPER_SWEDISH_FREQ = 420
        const val SWEDISH_PENALTY = 120
        const val RESTORE_BLOCKING_ENGLISH_FREQ = 300

        /** Loads the lists bundled under /tyst on the classpath. */
        fun loadDefault(): Lexicon {
            fun lines(name: String): Sequence<String> {
                val stream = Lexicon::class.java.getResourceAsStream("/tyst/$name")
                    ?: error("Missing resource /tyst/$name")
                return stream.bufferedReader(Charsets.UTF_8).readLines().asSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
            }
            fun freq(name: String): Map<String, Int> {
                val m = HashMap<String, Int>(80_000)
                for (line in lines(name)) {
                    val tab = line.indexOf('\t')
                    if (tab > 0) m[line.substring(0, tab)] = line.substring(tab + 1).trim().toInt()
                }
                return m
            }
            val miss = HashMap<String, Misspelling>()
            for (line in lines("misspellings.txt")) {
                val parts = line.split('\t')
                if (parts.size >= 2) miss[parts[0]] = Misspelling(parts[1], parts.getOrNull(2) == "ctx")
            }
            return Lexicon(
                english = freq("en_words.txt"),
                swedish = freq("sv_words.txt"),
                misspellings = miss,
                properEnglish = lines("proper_en.txt").toList(),
                properSwedish = lines("proper_sv.txt").toList(),
                swedishLoanWords = lines("swedish_words.txt").toList(),
            )
        }
    }
}
