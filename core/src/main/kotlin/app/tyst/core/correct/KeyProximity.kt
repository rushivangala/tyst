package app.tyst.core.correct

import kotlin.math.hypot

/** QWERTY key positions, used to make typos between neighbouring keys cheap. */
object KeyProximity {
    private val rows = listOf("qwertyuiop" to 0.0, "asdfghjkl" to 0.5, "zxcvbnm" to 1.5)
    private val pos: Map<Char, Pair<Double, Double>> = buildMap {
        rows.forEachIndexed { r, (keys, offset) -> keys.forEachIndexed { i, c -> put(c, (i + offset) to r.toDouble()) } }
    }
    private val diacritics = mapOf('å' to 'a', 'ä' to 'a', 'ö' to 'o', 'é' to 'e', 'ü' to 'u')

    fun base(c: Char): Char = diacritics[c] ?: c

    fun adjacent(a: Char, b: Char): Boolean {
        val pa = pos[base(a)] ?: return false
        val pb = pos[base(b)] ?: return false
        return hypot(pa.first - pb.first, pa.second - pb.second) <= 1.2
    }

    fun neighbours(c: Char): Set<Char> = pos.keys.filter { it != c && adjacent(it, c) }.toSet()

    /** Cost of typing [typed] when [intended] was meant. */
    fun substitutionCost(intended: Char, typed: Char): Double = when {
        intended == typed -> 0.0
        base(intended) == base(typed) -> 0.3
        adjacent(intended, typed) -> 0.6
        else -> 1.0
    }
}

/** Damerau–Levenshtein distance weighted by [KeyProximity]; returns > [max] early when exceeded. */
fun typoDistance(intended: String, typed: String, max: Double): Double {
    val n = intended.length
    val m = typed.length
    if (kotlin.math.abs(n - m) > max) return max + 1
    val d = Array(n + 1) { DoubleArray(m + 1) }
    for (i in 0..n) d[i][0] = i.toDouble()
    for (j in 0..m) d[0][j] = j.toDouble()
    for (i in 1..n) {
        var rowMin = Double.MAX_VALUE
        for (j in 1..m) {
            val a = intended[i - 1]
            val b = typed[j - 1]
            // A doubled or dropped repeat letter ("tomorow") is a cheap slip.
            val delCost = if (i >= 2 && intended[i - 2] == a) 0.6 else 1.0
            val insCost = if (j >= 2 && typed[j - 2] == b) 0.6 else 1.0
            var v = minOf(
                d[i - 1][j] + delCost,
                d[i][j - 1] + insCost,
                d[i - 1][j - 1] + KeyProximity.substitutionCost(a, b),
            )
            if (i > 1 && j > 1 && a == typed[j - 2] && intended[i - 2] == b) v = minOf(v, d[i - 2][j - 2] + 0.7)
            d[i][j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > max) return max + 1
    }
    return d[n][m]
}
