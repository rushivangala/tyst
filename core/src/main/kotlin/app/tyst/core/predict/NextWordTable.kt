package app.tyst.core.predict

/**
 * Likeliest next words for common English words, from `/tyst/en_next.txt` (see tools/gen_bigrams.py).
 * Read-only after loading; stored as parallel arrays to stay small on the phone.
 */
class NextWordTable private constructor(
    private val index: HashMap<String, Int>,
    private val words: Array<Array<String>>,
    private val probs: Array<FloatArray>,
) {
    val size: Int get() = index.size

    /** Calls [block] with each likely next word after [word] and its probability, likeliest first. */
    inline fun forEach(word: String, block: (String, Double) -> Unit) {
        val i = indexOf(word)
        if (i < 0) return
        val ws = wordsAt(i)
        val ps = probsAt(i)
        for (k in ws.indices) block(ws[k], ps[k].toDouble())
    }

    fun indexOf(word: String): Int = index[word] ?: -1
    fun wordsAt(i: Int): Array<String> = words[i]
    fun probsAt(i: Int): FloatArray = probs[i]

    companion object {
        fun load(resource: String = "/tyst/en_next.txt"): NextWordTable? {
            val stream = NextWordTable::class.java.getResourceAsStream(resource) ?: return null
            val index = HashMap<String, Int>()
            val words = ArrayList<Array<String>>()
            val probs = ArrayList<FloatArray>()
            // Followers repeat across heads ("the", "a", "to"); share one String per word.
            val pool = HashMap<String, String>()
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0] == '#') continue
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val parts = line.substring(tab + 1).split(' ')
                    val ws = ArrayList<String>(parts.size)
                    val ps = FloatArray(parts.size)
                    for (p in parts) {
                        val colon = p.lastIndexOf(':')
                        if (colon <= 0) continue
                        val permille = p.substring(colon + 1).toIntOrNull() ?: continue
                        val w = p.substring(0, colon)
                        ps[ws.size] = permille / 1000f
                        ws += pool.getOrPut(w) { w }
                    }
                    if (ws.isEmpty()) continue
                    index[line.substring(0, tab)] = words.size
                    words += ws.toTypedArray()
                    probs += ps.copyOf(ws.size)
                }
            }
            return NextWordTable(index, words.toTypedArray(), probs.toTypedArray())
        }
    }
}
