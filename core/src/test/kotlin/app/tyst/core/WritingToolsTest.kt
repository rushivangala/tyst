package app.tyst.core

import app.tyst.core.style.StyleProfile
import app.tyst.core.write.FixKind
import app.tyst.core.write.Proofreader
import app.tyst.core.write.RewriteError
import app.tyst.core.write.RewriteException
import app.tyst.core.write.Rewriter
import app.tyst.core.write.Tone
import app.tyst.core.write.WordDiff
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WritingToolsTest {
    private val neutral = "Hi Elin, I can't make it to the meeting tomorrow. Can we move it to Thursday afternoon? Let me know if that works."
    private val proof = Proofreader(Fixtures.Engine().autocorrector)

    private fun rewrite(text: String, tone: Tone, style: StyleProfile = StyleProfile(), app: String = "com.slack") =
        Rewriter(style, Fixtures.lexicon).rewrite(text, tone, app).getOrThrow()

    @Test fun formal() {
        val r = rewrite(neutral, Tone.FORMAL)
        assertThat(r.text).startsWith("Dear Elin,\n")
        assertThat(r.text).contains("I am unable to attend the meeting tomorrow.")
        assertThat(r.text).contains("Would it be possible to move it to Thursday afternoon?")
        assertThat(r.text).contains("Please let me know whether this suits you.")
        assertThat(r.text).endsWith("Kind regards,")
        assertThat(r.text).doesNotContain("can't")
    }

    @Test fun warmer() {
        val r = rewrite(neutral, Tone.WARMER)
        assertThat(r.text).startsWith("Hi Elin! So sorry, I can't make it")
        assertThat(r.text).contains("Could we move it")
        assertThat(r.text).endsWith("Thanks so much!")
    }

    @Test fun shorter() {
        val r = rewrite("Hi Elin, I hope you are well. I just wanted to say that I am really not able to come in order to help.", Tone.SHORTER)
        assertThat(r.text).isEqualTo("Elin, I can't come to help.")
        assertThat(r.text.length).isLessThan(60)
    }

    @Test fun longer() {
        val r = rewrite("Hi Elin, pls send the file tmrw", Tone.LONGER)
        assertThat(r.text).startsWith("Hi Elin, I hope you're well. Please send the file tomorrow")
        assertThat(r.text).contains("Let me know if you have any questions.")
    }

    @Test fun swedishWordsAndNamesStayExactly() {
        for (tone in Tone.entries) {
            val r = rewrite("Hi Åsa, I can't do fika at Liseberg in Västerås tomorrow.", tone)
            assertThat(r.text).contains("fika")
            assertThat(r.text).contains("Liseberg")
            assertThat(r.text).contains("Västerås")
        }
    }

    @Test fun myStyleWithoutHistoryIsNeutralAndSaysSo() {
        val r = rewrite("I am running late, see you at six", Tone.MY_STYLE)
        assertThat(r.text).isEqualTo("I'm running late, see you at six")
        assertThat(r.notes.single()).contains("Not enough")
    }

    @Test fun myStyleFollowsLearnedHabits() {
        val style = StyleProfile()
        val msgs = listOf("Running late, there at 6!", "Can't do lunch, sorry!", "I'm at 5 then 😊", "Don't wait for me!", "See you at 7 😊", "Thanks, that's great!")
        msgs.forEach { style.learn(it, "com.slack", 0) }
        val habits = style.habits().map { it.id }
        assertThat(habits).containsAtLeast(StyleProfile.SHORT, StyleProfile.CASUAL, StyleProfile.DIGITS, StyleProfile.EMOJI_ID)
        val r = rewrite("I am very sorry, I will be there at six.", Tone.MY_STYLE, style)
        assertThat(r.text).isEqualTo("I'm sorry, I'll be there at 6! 😊")
        assertThat(r.notes.joinToString()).contains("Digits for times")
        assertThat(r.notes.last()).endsWith("your Slack style")

        style.setHabitEnabled(StyleProfile.DIGITS, false)
        assertThat(rewrite("I will be there at six.", Tone.MY_STYLE, style).text).contains("six")
    }

    @Test fun styleProfileRoundTrips() {
        val style = StyleProfile()
        repeat(6) { style.learn("Thanks for flagging this, see you at 6 😊", "com.slack", 5) }
        style.setHabitEnabled(StyleProfile.EMOJI_ID, false)
        val copy = StyleProfile.decode(style.encode())
        assertThat(copy.encode()).isEqualTo(style.encode())
        assertThat(copy.habits()).isEqualTo(style.habits())
        assertThat(copy.commonPhrases()).contains("thanks for flagging this")
    }

    @Test fun rewriteLimits() {
        val r = Rewriter(StyleProfile()).rewrite("x".repeat(Rewriter.MAX_CHARS + 1), Tone.SHORTER, "")
        assertThat((r.exceptionOrNull() as RewriteException).error).isEqualTo(RewriteError.TooLong)
        assertThat((Rewriter(StyleProfile()).rewrite(" ", Tone.SHORTER, "").exceptionOrNull() as RewriteException).error).isEqualTo(RewriteError.Empty)
    }

    @Test fun diffMarksChangedWords() {
        val ranges = WordDiff.changedRanges("I can't come", "I cannot come")
        assertThat(ranges).hasSize(1)
        assertThat("I cannot come".substring(ranges[0])).isEqualTo("cannot")
    }

    @Test fun proofreadFindsAndFixes() {
        val text = "i think teh meeting is is at Liseberg.  it was a honour , more then fine"
        val fixes = proof.check(text)
        val fixed = proof.apply(text, fixes)
        assertThat(fixed).isEqualTo("I think the meeting is at Liseberg. It was an honour, more than fine")
        assertThat(fixes.map { it.kind }.toSet()).containsAtLeast(FixKind.SPELLING, FixKind.GRAMMAR, FixKind.CAPITALS, FixKind.PUNCTUATION)
    }

    @Test fun proofreadLeavesSwedishAndCleanTextAlone() {
        assertThat(proof.check("Are you joining us for fika in Västerås? Åsa is coming.")).isEmpty()
        assertThat(proof.check("Email me at elin.berg@hey.com. Thanks!")).isEmpty()
        assertThat(proof.check("An hour, a user, an apple and a European trip.")).isEmpty()
    }

    @Test fun proofreadRestoresDiacritics() {
        val fixes = proof.check("The train to Vasteras is late.")
        assertThat(fixes.single().replacement).isEqualTo("Västerås")
    }
}
