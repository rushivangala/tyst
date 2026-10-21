package app.tyst.core

import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.correct.CorrectionOptions
import app.tyst.core.correct.CorrectionReason
import app.tyst.core.predict.PersonalModel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The ten cases from the UX copy (section 03), plus guards from the spec. */
class AutocorrectTest {
    private val engine = Fixtures.Engine()
    private val opts = CorrectionOptions()
    private fun check(word: String, before: List<String> = listOf("<s>"), o: CorrectionOptions = opts) =
        engine.autocorrector.check(word, before, o)

    @ParameterizedTest
    @CsvSource("teh,the", "recieve,receive", "definately,definitely", "im,I'm", "tomorow,tomorrow", "seperate,separate")
    fun fixesCommonTypos(typed: String, expected: String) {
        assertThat(check(typed).autoApply).isEqualTo(expected)
    }

    @Test fun wontOnlyWhenTheSentenceNeedsIt() {
        assertThat(check("wont", listOf("<s>", "i")).autoApply).isEqualTo("won't")
        assertThat(check("wont", listOf("the")).autoApply).isNull()
    }

    @Test fun leavesSwedishWordsAlone() {
        for (w in listOf("fika", "Liseberg", "Järntorget", "Västerås", "Åsa", "lagom", "Göteborg")) {
            val r = check(w, listOf("for"))
            assertWithMessage(w).that(r.autoApply).isNull()
            assertWithMessage(w).that(r.suggestions).isEmpty()
        }
    }

    @Test fun restoresDiacriticsOnlyAsASuggestion() {
        val r = check("Vasteras", listOf("to"))
        assertThat(r.autoApply).isNull()
        assertThat(r.reason).isEqualTo(CorrectionReason.RESTORE)
        assertThat(r.suggestions).containsExactly("Västerås")
        assertThat(check("Goteborg", listOf("in")).suggestions).containsExactly("Göteborg")
    }

    @Test fun restoreCanBeTurnedOff() {
        val r = check("Vasteras", listOf("to"), opts.copy(restoreDiacritics = false))
        assertThat(r.suggestions).doesNotContain("Västerås")
    }

    @Test fun keepsCaseOfTypedWord() {
        assertThat(check("Teh").autoApply).isEqualTo("The")
        assertThat(check("i", listOf("and")).autoApply).isEqualTo("I")
    }

    @Test fun knownWordsUntouched() {
        for (w in listOf("the", "meeting", "lunch", "noon", "receive")) assertWithMessage(w).that(check(w).autoApply).isNull()
    }

    @Test fun keyboardNeighbourTypos() {
        assertThat(check("meetinf", listOf("the")).autoApply).isEqualTo("meeting")
        assertThat(check("lunvh", listOf("for")).autoApply).isEqualTo("lunch")
    }

    @Test fun offOnlySuggests() {
        val r = check("teh", o = opts.copy(strength = AutocorrectStrength.OFF))
        assertThat(r.autoApply).isNull()
        assertThat(r.suggestions).contains("the")
    }

    @Test fun unknownCapitalisedWordMidSentenceIsNotChanged() {
        assertThat(check("Mettis", listOf("with")).autoApply).isNull()
    }

    @Test fun personalWordsAreNotCorrected() {
        val personal = PersonalModel()
        personal.addWord("Tysta", "test", 0)
        val e = Fixtures.Engine(personal)
        assertThat(e.autocorrector.check("Tysta", listOf("<s>"), opts).autoApply).isNull()
        personal.addNeverCorrect("blorp")
        assertThat(e.autocorrector.check("blorp", listOf("<s>"), opts).autoApply).isNull()
    }

    @Test fun swedishLayerOffAllowsCorrectionOfSwedishOnlyWords() {
        val r = check("lagom", listOf("is"), opts.copy(swedishWords = false))
        assertThat(r.reason).isNotEqualTo(CorrectionReason.SWEDISH_WORD)
    }
}
