package app.tyst.core

import app.tyst.core.field.Android
import app.tyst.core.field.FieldClassifier
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.text.Words
import app.tyst.core.typing.TypingController
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GesturesTest {
    private val controller = TypingController(TystEngine(Fixtures.lexicon, Fixtures.base))
    private val field = FieldClassifier.classify(Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_FLAG_CAP_SENTENCES, Android.IME_ACTION_SEND, "com.whatsapp")

    private fun start(text: String): FakeTarget = FakeTarget(text).also { controller.start(field, it, KeyboardSettings()) }

    @Test fun spaceSlideMovesTheCursorByCharacters() {
        val t = start("Hello there")
        controller.moveCursor(-5)
        assertThat(t.cursor).isEqualTo(6)
        controller.moveCursor(2)
        assertThat(t.cursor).isEqualTo(8)
        controller.moveCursor(100)
        assertThat(t.cursor).isEqualTo(11)
    }

    @Test fun cursorSlideSkipsWholeEmoji() {
        val t = start("ok 👍🏽 👨‍👩‍👧 🇸🇪")
        controller.moveCursor(-1)
        assertThat(t.value.substring(t.cursor)).isEqualTo("🇸🇪")
        controller.moveCursor(-2)
        assertThat(t.value.substring(t.cursor)).isEqualTo("👨‍👩‍👧 🇸🇪")
        controller.moveCursor(1)
        assertThat(t.value.substring(t.cursor)).isEqualTo(" 🇸🇪")
    }

    @Test fun cursorSlideUsesArrowKeysWhenOffsetsAreUnknown() {
        val t = start("Hello")
        t.reportsOffsets = false
        controller.moveCursor(-2)
        assertThat(t.arrowKeyPresses).isEqualTo(2)
        assertThat(t.cursor).isEqualTo(3)
    }

    @Test fun cursorSlideForgetsPendingUndo() {
        val t = start("")
        "Meet me at teh ".forEach { if (it == ' ') controller.onSpace() else controller.onText(it.toString()) }
        assertThat(controller.strip().undoWord).isEqualTo("teh")
        controller.moveCursor(-1)
        assertThat(controller.strip().undoWord).isNull()
        assertThat(t.value).isEqualTo("Meet me at the ")
    }

    @Test fun backspaceSlideSelectsWordsThenDeletes() {
        val t = start("See you at the cafe")
        controller.startDeleteSlide()
        controller.deleteSlide(1)
        assertThat(t.selected()).isEqualTo("cafe")
        controller.deleteSlide(2)
        assertThat(t.selected()).isEqualTo("the cafe")
        controller.endDeleteSlide(commit = true)
        // The space before stays, ready for the next word.
        assertThat(t.value).isEqualTo("See you at ")
    }

    @Test fun backspaceSlideBackToStartDeletesNothing() {
        val t = start("See you soon")
        controller.startDeleteSlide()
        controller.deleteSlide(2)
        controller.deleteSlide(0)
        controller.endDeleteSlide(commit = true)
        assertThat(t.value).isEqualTo("See you soon")
        assertThat(t.cursor).isEqualTo(12)
    }

    @Test fun backspaceSlideCancelPutsTheCursorBack() {
        val t = start("See you soon")
        controller.startDeleteSlide()
        controller.deleteSlide(1)
        controller.endDeleteSlide(commit = false)
        assertThat(t.value).isEqualTo("See you soon")
        assertThat(t.selected()).isEmpty()
        assertThat(t.cursor).isEqualTo(12)
    }

    @Test fun backspaceSlideWorksWithoutOffsets() {
        val t = start("See you soon.")
        t.reportsOffsets = false
        controller.startDeleteSlide()
        controller.deleteSlide(1)
        controller.endDeleteSlide(commit = true)
        // The full stop counts as the last word.
        assertThat(t.value).isEqualTo("See you soon")
    }

    @Test fun lastWordsLengthCountsPunctuationAsAWord() {
        assertThat(Words.lastWordsLength("Hi there, friend!", 1)).isEqualTo(1)
        assertThat(Words.lastWordsLength("Hi there, friend!", 2)).isEqualTo(7)
        assertThat(Words.lastWordsLength("Hi there ", 1)).isEqualTo(6)
        assertThat(Words.lastWordsLength("Hi", 5)).isEqualTo(2)
    }
}
