package app.tyst.core.bench

import app.tyst.core.FakeTarget
import app.tyst.core.correct.TapOffset
import app.tyst.core.layout.of
import app.tyst.core.Fixtures
import app.tyst.core.TystEngine
import app.tyst.core.field.Android
import app.tyst.core.field.FieldClassifier
import app.tyst.core.layout.KeyGeometry
import app.tyst.core.layout.KeyType
import app.tyst.core.layout.Layouts
import app.tyst.core.predict.PersonalModel
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.typing.ShiftState
import app.tyst.core.typing.TypingController
import java.util.Random
import kotlin.math.roundToInt

/**
 * Measures how well Tyst types, end to end through [TypingController], on held-out messages
 * (`/bench/messages.txt`) that the language model never saw.
 *
 * - Accuracy: a simulated thumb taps each letter with Gaussian scatter around the key centre on a
 *   phone-sized layout. Words the finger got right must stay right (false corrections); words it got
 *   wrong should be fixed by autocorrect.
 * - Keystroke savings: a perfect typist who taps a suggestion as soon as the wanted word shows in the
 *   strip. 1 − taps ÷ characters.
 * - Latency: time to build the strip after each key.
 *
 * Run `main` for the full report; [TypingBenchmarkTest] holds the regression gates.
 */
object TypingBenchmark {
    data class Report(
        val words: Int,
        val cleanWords: Int,
        val falseCorrections: Int,
        val typoWords: Int,
        val typosFixed: Int,
        val keystrokeSavings: Double,
        val nextWordTop3: Double,
        val p50Ms: Double,
        val p95Ms: Double,
        val examples: List<String>,
    ) {
        val falseCorrectionRate get() = falseCorrections.toDouble() / cleanWords.coerceAtLeast(1)
        val typoFixRate get() = typosFixed.toDouble() / typoWords.coerceAtLeast(1)

        fun format(): String = buildString {
            appendLine("Typing benchmark — $words words")
            appendLine("  false corrections : ${pct(falseCorrectionRate)} ($falseCorrections of $cleanWords correctly typed words changed)")
            appendLine("  typos fixed       : ${pct(typoFixRate)} ($typosFixed of $typoWords mistyped words)")
            appendLine("  keystroke savings : ${pct(keystrokeSavings)}")
            appendLine("  next word in top 3: ${pct(nextWordTop3)}")
            appendLine("  strip latency     : p50 ${"%.2f".format(p50Ms)} ms, p95 ${"%.2f".format(p95Ms)} ms")
            if (examples.isNotEmpty()) {
                appendLine("  examples (intended → typed → result):")
                examples.forEach { appendLine("    $it") }
            }
        }

        private fun pct(x: Double) = "%.1f%%".format(x * 100)
    }

    /** Which messages to use. Tune parameters on [TUNE] only; report [TEST] (or [ALL]). */
    enum class Part { ALL, TUNE, TEST }

    data class Config(
        val part: Part = Part.ALL,
        /** Tap scatter as a fraction of key width / height (1 SD). ~0.2 matches thumb-typing studies. */
        val sigmaX: Double = 0.21,
        val sigmaY: Double = 0.17,
        val seed: Long = 42,
        val runs: Int = 3,
        val settings: KeyboardSettings = KeyboardSettings(learning = false),
        val maxExamples: Int = 25,
        /** Pass where each tap landed to the keyboard, as the app does. */
        val useTaps: Boolean = true,
    )

    private val field = FieldClassifier.classify(
        Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_VARIATION_SHORT_MESSAGE or Android.TYPE_TEXT_FLAG_CAP_SENTENCES,
        Android.IME_ACTION_SEND, "com.whatsapp",
    )

    fun messages(part: Part = Part.ALL): List<String> =
        TypingBenchmark::class.java.getResourceAsStream("/bench/messages.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .filterIndexed { i, _ -> part == Part.ALL || (i % 2 == 0) == (part == Part.TUNE) }

    fun engine(personal: PersonalModel = PersonalModel()) = TystEngine(Fixtures.lexicon, Fixtures.base, personal)

    fun run(config: Config = Config(), newEngine: () -> TystEngine = { engine() }): Report {
        val msgs = messages(config.part)
        val timings = ArrayList<Long>()
        var words = 0; var clean = 0; var falseCorr = 0; var typo = 0; var fixed = 0
        val examples = ArrayList<String>()
        repeat(config.runs) { run ->
            val engine = newEngine()
            val sim = Thumb(config, config.seed + run)
            for (msg in msgs) {
                val controller = TypingController(engine)
                val t = FakeTarget()
                controller.start(field, t, config.settings)
                val typedWords = ArrayList<String>()
                typeNoisy(controller, msg, sim, typedWords, timings)
                val intended = tokens(msg)
                val result = tokens(t.value)
                if (result.size != intended.size) {
                    words += intended.size
                    typo += intended.size
                    if (examples.size < config.maxExamples) examples += "MISALIGNED: \"$msg\" → \"${t.value}\""
                    continue
                }
                for (i in intended.indices) {
                    words++
                    val want = intended[i]; val typed = typedWords.getOrElse(i) { "" }; val got = result[i]
                    if (typed == want) {
                        clean++
                        if (got != want) {
                            falseCorr++
                            if (examples.size < config.maxExamples) examples += "BROKE  $want → $typed → $got"
                        }
                    } else {
                        typo++
                        if (got == want) fixed++
                        else if (examples.size < config.maxExamples && run == 0) examples += "MISSED $want → $typed → $got"
                    }
                }
            }
        }
        val (ksr, nextTop3) = keystrokeSavings(newEngine(), msgs, config.settings, timings)
        timings.sort()
        fun pctl(p: Double) = if (timings.isEmpty()) 0.0 else timings[((timings.size - 1) * p).roundToInt()] / 1e6
        return Report(words, clean, falseCorr, typo, fixed, ksr, nextTop3, pctl(0.5), pctl(0.95), examples)
    }

    /** Words with their trailing punctuation removed; what a reader compares. */
    fun tokens(s: String): List<String> =
        s.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.trimEnd('.', ',', '!', '?', ';', ':') }.filter { it.isNotEmpty() }

    private fun typeNoisy(c: TypingController, msg: String, sim: Thumb, typedWords: MutableList<String>, timings: MutableList<Long>) {
        val word = StringBuilder()
        fun endWord() { if (word.isNotEmpty()) { typedWords += word.toString(); word.clear() } }
        for (ch in msg) {
            when {
                ch == ' ' -> { endWord(); c.onSpace() }
                ch.isLetter() && ch.lowercaseChar() in sim.letters -> {
                    // People press shift for a capital the keyboard didn't capitalise by itself.
                    if (ch.isUpperCase() && c.shift == ShiftState.OFF) c.onShift()
                    if (ch.isLowerCase() && c.shift == ShiftState.ON) c.onShift()
                    val (hit, offset) = sim.tap(ch.lowercaseChar())
                    c.onText(hit.toString(), if (sim.useTaps) offset else null)
                    word.append(if (ch.isUpperCase()) hit.uppercaseChar() else hit)
                }
                else -> {
                    if (ch.isLetter() && ch.isUpperCase() && c.shift == ShiftState.OFF) c.onShift()
                    c.onText(ch.toString())
                    if (ch.isLetterOrDigit() || ch == '\'') word.append(ch)
                }
            }
            val t0 = System.nanoTime(); c.strip(); timings += System.nanoTime() - t0
        }
        endWord()
    }

    /** A perfect typist who uses the strip: taps a suggestion as soon as the wanted word is offered. */
    private fun keystrokeSavings(engine: TystEngine, msgs: List<String>, settings: KeyboardSettings, timings: MutableList<Long>): Pair<Double, Double> {
        var chars = 0; var taps = 0; var nextAsked = 0; var nextHit = 0
        for (msg in msgs) {
            val c = TypingController(engine)
            val t = FakeTarget()
            c.start(field, t, settings)
            chars += msg.length
            val words = msg.split(' ')
            for ((wi, raw) in words.withIndex()) {
                val core = raw.trimEnd('.', ',', '!', '?')
                val punct = raw.substring(core.length)
                var typed = 0
                var picked = false
                while (typed <= core.length) {
                    val t0 = System.nanoTime(); val strip = c.strip(); timings += System.nanoTime() - t0
                    val match = strip.suggestions.firstOrNull { it.text == core }
                    if (typed == 0 && wi > 0 && core.any { it.isLetter() }) { nextAsked++; if (match != null) nextHit++ }
                    if (match != null && typed < core.length) {
                        c.pick(match); taps++; picked = true; break
                    }
                    if (typed == core.length) break
                    val ch = core[typed]
                    if (ch.isUpperCase() && c.shift == ShiftState.OFF) { c.onShift(); taps++ }
                    if (ch.isLowerCase() && c.shift == ShiftState.ON) { c.onShift(); taps++ }
                    c.onText(ch.toString()); taps++
                    typed++
                }
                for (p in punct) { c.onText(p.toString()); taps++ }
                val last = wi == words.size - 1
                if (!last && !(picked && punct.isEmpty())) { c.onSpace(); taps++ }
            }
        }
        return (1.0 - taps.toDouble() / chars) to (nextHit.toDouble() / nextAsked.coerceAtLeast(1))
    }

    /** Taps keys with Gaussian scatter on a 412 dp wide phone layout. */
    class Thumb(private val config: Config, seed: Long) {
        private val rnd = Random(seed)
        private val geometry = KeyGeometry(Layouts.letters(numberRow = false, swedishRow = false), 412f, 52f, 6f, 10f, 3f, 8f, 8f)
        private val centres = geometry.keys.filter { it.key.type == KeyType.CHAR && it.key.text.length == 1 && it.key.text[0].isLetter() }
            .associate { it.key.text[0] to it }
        val letters: Set<Char> = centres.keys
        val useTaps = config.useTaps

        /** The key hit when aiming at [c], and where on that key the finger landed (as KeyboardView reports it). */
        fun tap(c: Char): Pair<Char, TapOffset?> {
            val k = centres[c] ?: return c to null
            val x = (k.visual.centerX + rnd.nextGaussian() * config.sigmaX * k.slot.width).toFloat()
            val y = (k.visual.centerY + rnd.nextGaussian() * config.sigmaY * k.slot.height).toFloat()
            val hit = geometry.keyAt(x, y) ?: return c to null
            if (hit.key.type != KeyType.CHAR || hit.key.text.length != 1 || !hit.key.text[0].isLetter()) return c to null
            return hit.key.text[0] to TapOffset.of(hit, x, y)
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "sweep-following" -> for (w in listOf(0.0, 0.3, 0.6, 1.0, 1.5, 2.5)) {
                Fixtures.base.followingWeight = w
                val r = run(Config(part = Part.TUNE, runs = 1))
                println("following=$w  ksr=${"%.3f".format(r.keystrokeSavings)} next3=${"%.3f".format(r.nextWordTop3)} fix=${"%.3f".format(r.typoFixRate)} false=${"%.4f".format(r.falseCorrectionRate)}")
            }
            "sweep-distance" -> for (w in listOf(3.0, 3.5, 4.0, 4.5, 5.0)) {
                val r = run(Config(part = Part.TUNE, runs = 2)) { engine().also { it.autocorrector.distanceWeight = w } }
                println("distance=$w  fix=${"%.3f".format(r.typoFixRate)} false=${"%.4f".format(r.falseCorrectionRate)} ksr=${"%.3f".format(r.keystrokeSavings)}")
            }
            "taps" -> for (taps in listOf(false, true)) {
                val r = run(Config(part = Part.TUNE, runs = 2, useTaps = taps))
                println("taps=$taps  fix=${"%.3f".format(r.typoFixRate)} false=${"%.4f".format(r.falseCorrectionRate)} (${r.falseCorrections})")
            }
            "test" -> println(run(Config(part = Part.TEST)).format())
            else -> println(run().format())
        }
    }
}
