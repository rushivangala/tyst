package app.tyst.core

import app.tyst.core.correct.CorrectionOptions
import app.tyst.core.predict.NGramModel
import app.tyst.core.predict.PersonalModel
import app.tyst.core.predict.SuggestionKind
import app.tyst.core.text.Words
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PredictorTest {
    private val opts = CorrectionOptions()

    private fun texts(e: Fixtures.Engine, before: String) = e.predictor.predict(before, opts).suggestions.map { it.text }

    @Test fun nextWordFromBaseModel() {
        val e = Fixtures.Engine()
        assertThat(texts(e, "Can you send me the ")).containsAnyOf("report", "link", "file")
        assertThat(texts(e, "See you ")).containsAnyOf("soon", "later", "at", "tomorrow")
        assertThat(texts(e, "Thanks for flagging ")).containsAnyOf("this", "that")
    }

    @Test fun personalHistoryLeads() {
        val personal = PersonalModel()
        repeat(5) {
            val prev = Words.previousWords("Can we meet at Järntorget around ", 2)
            personal.onWordCommitted("6", prev, true, "messages", 0)
        }
        val e = Fixtures.Engine(personal)
        assertThat(texts(e, "Can we meet at Järntorget around ").first()).isEqualTo("6")
    }

    @Test fun completesSwedishPlaceWithDiacritics() {
        val e = Fixtures.Engine()
        val s = texts(e, "See you at lunch in Väst")
        assertThat(s).contains("Västerås")
    }

    @Test fun capitalisesAtSentenceStart() {
        val e = Fixtures.Engine()
        val s = e.predictor.predict("", opts).suggestions
        assertThat(s).hasSize(3)
        assertThat(s.all { it.text.first().isUpperCase() }).isTrue()
    }

    @Test fun typoShowsCorrectionAndAutoApply() {
        val e = Fixtures.Engine()
        val p = e.predictor.predict("Can we meet at Järntorget around teh", opts)
        assertThat(p.autoCorrection).isEqualTo("the")
        assertThat(p.suggestions.first().text).isEqualTo("the")
        assertThat(p.suggestions.map { it.text }).contains("teh")
    }

    @Test fun knownTypedWordStaysCentre() {
        val e = Fixtures.Engine()
        val p = e.predictor.predict("Let's have lunch", opts)
        assertThat(p.suggestions.first().kind).isEqualTo(SuggestionKind.TYPED)
        assertThat(p.suggestions.first().text).isEqualTo("lunch")
        assertThat(p.autoCorrection).isNull()
    }

    @Test fun restoreSuggestionComesFirst() {
        val e = Fixtures.Engine()
        val p = e.predictor.predict("The train to Vasteras", opts)
        assertThat(p.suggestions.first().text).isEqualTo("Västerås")
        assertThat(p.autoCorrection).isNull()
    }

    @Test fun blockedWordsAreNotSuggested() {
        val personal = PersonalModel()
        personal.removeWord("lunch", block = true)
        val e = Fixtures.Engine(personal)
        assertThat(texts(e, "Are you joining us for lun")).doesNotContain("lunch")
    }

    @Test fun ngramPrunes() {
        val m = NGramModel()
        repeat(100) { m.addSentence(listOf("w$it", "x$it")) }
        m.addSentence(listOf("keep", "me")); m.addSentence(listOf("keep", "me"))
        m.prune(10)
        assertThat(m.bigrams["keep"]?.get("me")).isEqualTo(2)
        assertThat(m.bigrams.values.sumOf { it.size }).isAtMost(10)
    }

    @Test fun lexiconLoads() {
        assertThat(Fixtures.lexicon.englishSize).isGreaterThan(50_000)
        assertThat(Fixtures.lexicon.swedishSize).isGreaterThan(30_000)
        assertThat(Fixtures.lexicon.display("järntorget")).isEqualTo("Järntorget")
    }
}
