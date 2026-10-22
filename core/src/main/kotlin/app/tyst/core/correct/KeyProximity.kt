package app.tyst.core.correct

import kotlin.math.hypot
import kotlin.math.ln

/**
 * Where a finger landed on a letter key, relative to the key's centre: [dx] in key widths (right is
 * positive) and [dy] in row heights (down is positive). A tap on the border with the next key is ±0.5.
 */
data class TapOffset(val dx: Float, val dy: Float) {
    companion object
}

/** Substitution cost for intended letter [intended] at typed position [j]. */
fun interface SubstitutionCost {
    fun cost(intended: Char, j: Int): Double
}

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

    /**
     * Tap-aware cost: how much less likely it is that a finger aiming at [intended] landed where [tap] says
     * than one aiming at [typed]. With taps scattered as a Gaussian of [TAP_SIGMA] keys, that log10 ratio is
     * (d_intended² − d_typed²) / (2σ² ln 10); dividing by the distance weight puts it in typo-distance units.
     * A tap at the centre of the hit key costs about the same as [substitutionCost]'s 0.6; a tap on the
     * border between two keys costs almost nothing, because either key was equally likely meant.
     */
    fun tapCost(intended: Char, typed: Char, tap: TapOffset?, distanceWeight: Double = DISTANCE_UNIT_LOG10): Double {
        if (tap == null || intended == typed || base(intended) == base(typed)) return substitutionCost(intended, typed)
        val pi = pos[base(intended)] ?: return substitutionCost(intended, typed)
        val pt = pos[base(typed)] ?: return substitutionCost(intended, typed)
        val x = pt.first + tap.dx
        val y = pt.second + tap.dy
        val di2 = (x - pi.first) * (x - pi.first) + (y - pi.second) * (y - pi.second)
        val dt2 = (tap.dx * tap.dx + tap.dy * tap.dy).toDouble()
        val log10Ratio = (di2 - dt2) / (2 * TAP_SIGMA * TAP_SIGMA * ln(10.0))
        return (log10Ratio / distanceWeight).coerceIn(MIN_TAP_COST, 1.0)
    }

    /** Spread of thumb taps around the aimed-at key centre, in key widths. */
    const val TAP_SIGMA = 0.3
    /** Log10 units per unit of typo distance (Autocorrector.DISTANCE_WEIGHT). */
    const val DISTANCE_UNIT_LOG10 = 4.0
    private const val MIN_TAP_COST = 0.05
}

/**
 * Damerau–Levenshtein distance weighted by [KeyProximity]; returns > [max] early when exceeded.
 * [sub] overrides the substitution cost per typed position (for tap-aware costs).
 */
fun typoDistance(intended: String, typed: String, max: Double, sub: SubstitutionCost? = null): Double {
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
                d[i - 1][j - 1] + (sub?.cost(a, j - 1) ?: KeyProximity.substitutionCost(a, b)),
            )
            if (i > 1 && j > 1 && a == typed[j - 2] && intended[i - 2] == b) v = minOf(v, d[i - 2][j - 2] + 0.7)
            d[i][j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > max) return max + 1
    }
    return d[n][m]
}
