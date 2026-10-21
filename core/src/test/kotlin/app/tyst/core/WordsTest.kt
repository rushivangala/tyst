package app.tyst.core

import app.tyst.core.text.Words
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WordsTest {
    @Test fun currentWordStopsAtSeparators() {
        assertThat(Words.currentWord("Can we meet at Järn")).isEqualTo("Järn")
        assertThat(Words.currentWord("Can we ")).isEmpty()
        assertThat(Words.currentWord("I'm")).isEqualTo("I'm")
        assertThat(Words.currentWord("say 'hel")).isEqualTo("hel")
    }

    @Test fun previousWordsMarksSentenceStart() {
        assertThat(Words.previousWords("Hi. Can we", 3)).containsExactly("<s>", "can").inOrder()
        assertThat(Words.previousWords("Can we meet at Järntorget around ", 2)).containsExactly("järntorget", "around").inOrder()
        assertThat(Words.previousWords("", 2)).containsExactly("<s>")
        assertThat(Words.previousWords("Hello", 2)).containsExactly("<s>")
    }

    @Test fun sentenceStart() {
        assertThat(Words.isSentenceStart("")).isTrue()
        assertThat(Words.isSentenceStart("Hi. ")).isTrue()
        assertThat(Words.isSentenceStart("Hi.")).isFalse()
        assertThat(Words.isSentenceStart("Hi there ")).isFalse()
        assertThat(Words.isSentenceStart("Line one\n")).isTrue()
    }

    @Test fun matchCaseKeepsOwnCapitals() {
        assertThat(Words.matchCase("Teh", "the")).isEqualTo("The")
        assertThat(Words.matchCase("TEH", "the")).isEqualTo("THE")
        assertThat(Words.matchCase("vasteras", "Västerås")).isEqualTo("Västerås")
    }

    @Test fun graphemes() {
        assertThat(Words.lastGraphemeLength("hi")).isEqualTo(1)
        assertThat(Words.lastGraphemeLength("ok 👍")).isEqualTo(2)
        assertThat(Words.lastGraphemeLength("ok 👍🏽")).isEqualTo(4)
        assertThat(Words.lastGraphemeLength("a👨‍👩‍👧")).isEqualTo(8)
        assertThat(Words.lastGraphemeLength("")).isEqualTo(0)
    }

    @Test fun stripDiacritics() {
        assertThat(Words.stripDiacritics("Västerås")).isEqualTo("Vasteras")
    }
}
