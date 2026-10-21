package app.tyst.core

import app.tyst.core.correct.CorrectionOptions
import app.tyst.core.predict.PersonalModel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/** Corpus-level checks behind the spec's success metrics (§08). */
class QualityTest {
    private val engine = Fixtures.Engine()
    private val opts = CorrectionOptions()

    private fun resourceWords(name: String, limit: Int = Int.MAX_VALUE): List<String> =
        javaClass.getResourceAsStream("/tyst/$name")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringBefore('\t') }
            .take(limit)

    @Test fun swedishWordsWronglyCorrectedUnderHalfAPercent() {
        val words = resourceWords("proper_sv.txt") + resourceWords("swedish_words.txt") +
            resourceWords("sv_words.txt", 5000).filter { w -> w.any { it in "åäö" } }
        val wrong = words.filter { w -> engine.autocorrector.check(w, listOf("in"), opts).autoApply != null }
        val rate = wrong.size.toDouble() / words.size
        assertWithMessage("wrongly corrected: ${wrong.take(20)}").that(rate).isLessThan(0.005)
    }

    @Test fun commonEnglishWordsAreNeverChanged() {
        val words = resourceWords("en_words.txt", 10_000)
        val changed = words.filter { w -> engine.autocorrector.check(w, listOf("the"), opts).autoApply.let { it != null && it.lowercase() != w } }
        // Only contraction fixes ("dont" → "don't") may apply to frequent tokens.
        assertWithMessage("changed: ${changed.take(20)}").that(changed.all { engine.autocorrector.check(it, listOf("<s>"), opts).autoApply?.contains('\'') == true || it == "i" }).isTrue()
    }

    @Test fun predictionLatencyP95() {
        val inputs = listOf("Can we meet at Järntorget around ", "See you at lunch in Väst", "definately", "Thanks for flagg", "I wil", "recieved the", "Let's move the meeting to Thurs")
        repeat(20) { inputs.forEach { engine.predictor.predict(it, opts) } } // warm up the JIT
        val times = ArrayList<Long>()
        repeat(30) { inputs.forEach { s -> val t0 = System.nanoTime(); engine.predictor.predict(s, opts); times += System.nanoTime() - t0 } }
        times.sort()
        val p95ms = times[(times.size * 0.95).toInt()] / 1e6
        // Spec target on Pixel 10 Pro is < 30 ms; a desktop JVM should be well under that.
        assertWithMessage("p95 = $p95ms ms").that(p95ms).isLessThan(30.0)
    }

    @Test fun personalModelRoundTripsAndForgets() {
        val p = PersonalModel()
        p.onWordCommitted("Alma", listOf("<s>", "pick"), known = false, app = "com.whatsapp", now = 1)
        p.onWordCommitted("Alma", listOf("<s>", "pick"), known = false, app = "com.whatsapp", now = 2)
        p.addNeverCorrect("Järntorget")
        p.onEmojiUsed("😊")
        p.removeWord("spam", block = true)
        val copy = PersonalModel.decode(p.encode())
        assertThat(copy.dictionaryWords.map { it.word }).containsExactly("Alma")
        assertThat(copy.displayForm("alma")).isEqualTo("Alma")
        assertThat(copy.isNeverCorrect("järntorget")).isTrue()
        assertThat(copy.emojiUsage).containsEntry("😊", 1)
        assertThat(copy.isBlocked("spam")).isTrue()
        assertThat(copy.ngrams.bigrams["pick"]?.get("alma")).isEqualTo(2)
        copy.removeWord("Alma", block = false)
        assertThat(copy.inDictionary("alma")).isFalse()
        assertThat(copy.ngrams.bigrams["pick"]?.get("alma")).isNull()
        assertThat(PersonalModel.decode("garbage\nU\tx\tnotanumber\nU\tok\t3").count("ok")).isEqualTo(3)
    }

    @Test fun learnedNameIsPredictedWithItsCapital() {
        val p = PersonalModel()
        repeat(3) { p.onWordCommitted("Alma", listOf("pick", "up"), known = false, app = "a", now = 0) }
        val e = Fixtures.Engine(p)
        assertThat(e.predictor.predict("Don't forget to pick up ", opts).suggestions.map { it.text }).contains("Alma")
        // "Al" is a word, so it stays centre (space keeps it); Alma is the first completion beside it.
        assertThat(e.predictor.predict("Don't forget to pick up Al", opts).suggestions[1].text).isEqualTo("Alma")
    }
}
