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

class TypoSearchTest {
    /** The trie walk must find exactly what scoring every word with typoDistance finds. */
    @org.junit.jupiter.api.Test fun trieSearchMatchesBruteForce() {
        val lex = Fixtures.lexicon
        val typed = listOf("teh", "recieve", "xar", "geating", "anow", "deven", "ay", "pn", "goteborg", "vasteras",
            "tomorow", "definately", "meetinf", "thsnks", "kanelbulle", "qz", "a", "seperate", "lunchh", "wjat")
        for (t in typed) {
            val max = if (t.length >= 6) 2.0 else 1.5
            val first = (app.tyst.core.correct.KeyProximity.neighbours(t[0]) + t[0] + (t.getOrNull(1) ?: t[0])).toSet()
            val brute = HashMap<String, Double>()
            lex.forEachCandidate(first, t.length - 2, t.length + 2, true) { w, _ ->
                val d = app.tyst.core.correct.typoDistance(w, t, max)
                if (d <= max) brute[w] = d
            }
            val fast = HashMap<String, Double>()
            lex.forEachWithin(t, max, first, t.length - 2, t.length + 2, true) { w, _, d -> fast[w] = d }
            com.google.common.truth.Truth.assertWithMessage(t).that(fast.keys).isEqualTo(brute.keys)
            for ((w, d) in brute) com.google.common.truth.Truth.assertWithMessage("$t/$w").that(fast[w]!!).isWithin(1e-9).of(d)
        }
    }
}
