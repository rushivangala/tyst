package app.tyst.core

import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.field.Android
import app.tyst.core.field.EnterAction
import app.tyst.core.field.FieldClassifier
import app.tyst.core.memory.MemoryType
import app.tyst.core.predict.SuggestionKind
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.typing.ShiftState
import app.tyst.core.typing.TypingController
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TypingControllerTest {
    private var now = 1_000_000L
    private val engine = TystEngine(Fixtures.lexicon, Fixtures.base)
    private val controller = TypingController(engine) { now }
    private val message = FieldClassifier.classify(
        Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_VARIATION_SHORT_MESSAGE or Android.TYPE_TEXT_FLAG_CAP_SENTENCES,
        Android.IME_ACTION_SEND, "com.whatsapp",
    )

    private fun start(initial: String = "", settings: KeyboardSettings = KeyboardSettings(memoryEnabled = true)): FakeTarget {
        val t = FakeTarget(initial)
        controller.start(message, t, settings)
        return t
    }

    /** Types like a person: letters, space, punctuation. */
    private fun type(s: String) {
        for (c in s) when (c) {
            ' ' -> controller.onSpace()
            else -> controller.onText(c.toString())
        }
    }

    @Test fun autoCapitalisesSentenceStart() {
        val t = start()
        assertThat(controller.shift).isEqualTo(ShiftState.ON)
        type("hi there. how")
        assertThat(t.value).isEqualTo("Hi there. How")
    }

    @Test fun autocorrectsOnSpaceAndBackspaceRestores() {
        val t = start()
        type("Can we meet at Järntorget around teh ")
        assertThat(t.value).isEqualTo("Can we meet at Järntorget around the ")
        assertThat(controller.strip().undoWord).isEqualTo("teh")
        controller.onBackspace()
        assertThat(t.value).isEqualTo("Can we meet at Järntorget around teh")
        // Kept as typed: space doesn't correct it again.
        controller.onSpace()
        assertThat(t.value).isEqualTo("Can we meet at Järntorget around teh ")
    }

    @Test fun swedishWordsSurviveTyping() {
        val t = start()
        type("Are you joining us for fika at Liseberg in Göteborg ")
        assertThat(t.value).isEqualTo("Are you joining us for fika at Liseberg in Göteborg ")
    }

    @Test fun undoChipRestoresAndKeepsSpace() {
        val t = start()
        type("I recieve ")
        assertThat(t.value).isEqualTo("I receive ")
        controller.undoCorrection()
        assertThat(t.value).isEqualTo("I recieve ")
    }

    @Test fun twoUndosTeachTheWord() {
        repeat(2) {
            val t = start()
            type("Blåbärsmys teh ")
            controller.onBackspace()
            assertThat(t.value).endsWith("teh")
        }
        assertThat(engine.personal.inDictionary("teh")).isTrue()
        val t = start()
        type("teh ")
        assertThat(t.value).isEqualTo("Teh ")
    }

    @Test fun punctuationAppliesCorrectionAndAttachesAfterSuggestion() {
        val t = start()
        type("See you tomorow.")
        assertThat(t.value).isEqualTo("See you tomorrow.")
        val t2 = start()
        type("See you ")
        val s = controller.strip().suggestions.first { it.text == "soon" || it.text == "later" }
        controller.pick(s)
        assertThat(t2.value).isEqualTo("See you ${s.text} ")
        controller.onText(".")
        assertThat(t2.value).isEqualTo("See you ${s.text}. ")
    }

    @Test fun spaceAfterPickedSuggestionIsAbsorbedThenSecondAddsPeriod() {
        val t = start()
        type("See you ")
        val s = controller.strip().suggestions.first { it.text == "soon" || it.text == "later" }
        controller.pick(s)
        // Tapping space out of habit after a suggestion: no period, no double space.
        controller.onSpace()
        assertThat(t.value).isEqualTo("See you ${s.text} ")
        // A second tap is the usual double-space period.
        controller.onSpace()
        assertThat(t.value).isEqualTo("See you ${s.text}. ")
    }

    @Test fun writingToolsOnALongFieldWorkOnWholeSentences() {
        val sentences = (1..400).map { "Sentence number $it is here." }
        val full = sentences.joinToString(" ")
        val t = start(full)
        val window = controller.textForTools()
        assertThat(window.length).isAtMost(4000)
        assertThat(window).startsWith("Sentence number")
        assertThat(window).endsWith("is here.")
        controller.replaceText("Rewritten.")
        // Everything outside the window is untouched, and the seam is between sentences.
        assertThat(t.value).isEqualTo(full.removeSuffix(window) + "Rewritten.")
        assertThat(t.value).contains("is here. Rewritten.")
    }

    @Test fun doubleSpaceAddsPeriod() {
        val t = start()
        type("Sounds good  ")
        assertThat(t.value).isEqualTo("Sounds good. ")
        assertThat(controller.shift).isEqualTo(ShiftState.ON)
    }

    @Test fun pickingCompletionReplacesWord() {
        val t = start()
        type("See you at lunch in Väst")
        val s = controller.strip().suggestions.first { it.text == "Västerås" }
        controller.pick(s)
        assertThat(t.value).isEqualTo("See you at lunch in Västerås ")
    }

    @Test fun backspaceDeletesWholeEmoji() {
        val t = start()
        controller.onEmoji("👍🏽")
        controller.onBackspace()
        assertThat(t.value).isEmpty()
        controller.onBackspace()
        assertThat(t.rawBackspaces).isEqualTo(1)
    }

    @Test fun shiftDoubleTapLocks() {
        start("Hello ")
        assertThat(controller.shift).isEqualTo(ShiftState.OFF)
        controller.onShift(); now += 100; controller.onShift()
        assertThat(controller.shift).isEqualTo(ShiftState.LOCKED)
        type("ab")
        assertThat(controller.shift).isEqualTo(ShiftState.LOCKED)
        controller.onShift()
        assertThat(controller.shift).isEqualTo(ShiftState.OFF)
    }

    @Test fun enterSendsAndLearnsStyleAndMemory() {
        val t = start()
        type("My address is Kungsgatan 4, 411 19 Göteborg")
        controller.onEnter()
        assertThat(t.actions).containsExactly(EnterAction.SEND)
        assertThat(engine.style.messageCount).isEqualTo(1)
        val item = engine.memory.all.single()
        assertThat(item.type).isEqualTo(MemoryType.ADDRESS)
        assertThat(item.value).isEqualTo("Kungsgatan 4, 411 19 Göteborg")
    }

    @Test fun memoryChipAppearsAfterTriggerAndInserts() {
        engine.memory.add(MemoryType.ADDRESS, "Kungsgatan 4, 411 19 Göteborg", "test", now)
        val t = start()
        type("My address is ")
        val strip = controller.strip()
        assertThat(strip.memory).hasSize(1)
        controller.insertMemory(strip.memory.first())
        assertThat(t.value).isEqualTo("My address is Kungsgatan 4, 411 19 Göteborg")
    }

    @Test fun memoryIsOffUntilEnabled() {
        engine.memory.add(MemoryType.ADDRESS, "Kungsgatan 4", "test", now)
        start(settings = KeyboardSettings(memoryEnabled = false))
        type("My address is ")
        assertThat(controller.strip().memory).isEmpty()
        controller.onEnter()
        assertThat(engine.memory.size).isEqualTo(1)
    }

    @Test fun passwordFieldsAreIncognito() {
        val pw = FieldClassifier.classify(Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_VARIATION_PASSWORD, Android.IME_ACTION_DONE)
        val t = FakeTarget()
        controller.start(pw, t, KeyboardSettings(memoryEnabled = true))
        type("hunter2 ")
        controller.onEnter()
        val strip = controller.strip()
        assertThat(strip.incognito).isTrue()
        assertThat(strip.suggestions).isEmpty()
        assertThat(strip.writingTools).isFalse()
        assertThat(engine.personal.count("hunter2")).isEqualTo(0)
        assertThat(engine.style.messageCount).isEqualTo(0)
        assertThat(t.value).isEqualTo("hunter2 ")
    }

    @Test fun noPersonalizedLearningFlagIsHonoured() {
        val f = FieldClassifier.classify(Android.TYPE_CLASS_TEXT, Android.IME_FLAG_NO_PERSONALIZED_LEARNING)
        controller.start(f, FakeTarget(), KeyboardSettings())
        type("secretplace secretplace secretplace ")
        assertThat(engine.personal.count("secretplace")).isEqualTo(0)
        assertThat(controller.strip().incognito).isTrue()
    }

    @Test fun pausedLearningLearnsNothing() {
        start(settings = KeyboardSettings(learning = false))
        type("Blorptastic blorptastic ")
        controller.onEnter()
        assertThat(engine.personal.count("blorptastic")).isEqualTo(0)
        assertThat(engine.style.messageCount).isEqualTo(0)
    }

    @Test fun copiedCodeChipFillsAndExpires() {
        val code = FieldClassifier.classify(Android.TYPE_CLASS_NUMBER, Android.IME_ACTION_DONE, hint = "Verification code")
        val t = FakeTarget()
        controller.start(code, t, KeyboardSettings(codeExpirySeconds = 60))
        controller.onClipboard("Your verification code is 482913. Don't share it.")
        assertThat(controller.strip().code?.code).isEqualTo("482913")
        assertThat(controller.strip().codeSecondsLeft).isEqualTo(60)
        now += 45_000
        assertThat(controller.strip().codeSecondsLeft).isEqualTo(15)
        assertThat(controller.insertCode()).isTrue()
        assertThat(t.value).isEqualTo("482913")
        assertThat(controller.strip().code).isNull()

        controller.onClipboard("731 004")
        now += 61_000
        assertThat(controller.strip().code).isNull()
    }

    @Test fun codesCanBeHidden() {
        start(settings = KeyboardSettings(showCopiedCodes = false))
        controller.onClipboard("482913")
        assertThat(controller.strip().code).isNull()
    }

    @Test fun autocorrectOffNeverReplaces() {
        val t = start(settings = KeyboardSettings(autocorrect = AutocorrectStrength.OFF))
        type("teh ")
        assertThat(t.value).isEqualTo("Teh ")
    }

    @Test fun replaceTextUsesSelectionOrWholeField() {
        val t = start("Hello there friend")
        t.select(6, 11)
        assertThat(controller.textForTools()).isEqualTo("there")
        controller.replaceText("dear")
        assertThat(t.value).isEqualTo("Hello dear friend")
        t.select(t.value.length, t.value.length)
        controller.replaceText("Bye")
        assertThat(t.value).isEqualTo("Bye")
    }

    @Test fun nextWordSuggestionAfterSpace() {
        start()
        type("Can you send me the ")
        val strip = controller.strip()
        assertThat(strip.suggestions.map { it.kind }.toSet()).containsExactly(SuggestionKind.NEXT_WORD)
        assertThat(strip.suggestions.map { it.text }).containsAnyOf("report", "link", "file")
    }

    @Test fun finishLearnsNotes() {
        val notes = FieldClassifier.classify(Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_FLAG_MULTI_LINE, 0, "com.google.android.keep")
        val t = FakeTarget()
        controller.start(notes, t, KeyboardSettings())
        type("Buy milk and bread")
        controller.onEnter()
        assertThat(t.value).isEqualTo("Buy milk and bread\n")
        controller.finish()
        assertThat(engine.style.messageCount).isEqualTo(1)
    }

    @Test fun copiedTextIsOfferedAndPastes() {
        val t = start("Meet at ")
        controller.onClipboard("Kungsgatan 4, 411 19 Göteborg")
        val clip = controller.strip().clip!!
        assertThat(clip.preview()).isEqualTo("Kungsgatan 4, 411 19 Gö…")
        controller.insertClip()
        assertThat(t.value).isEqualTo("Meet at Kungsgatan 4, 411 19 Göteborg")
        assertThat(controller.strip().clip).isNull()
    }

    @Test fun copiedTextStepsAsideWhileTypingAWord() {
        start()
        controller.onClipboard("https://example.com/doc")
        type("Here")
        assertThat(controller.strip().clip).isNull()
        controller.onSpace()
        assertThat(controller.strip().clip?.text).isEqualTo("https://example.com/doc")
    }

    @Test fun copiedTextExpiresAfterThreeMinutesAndCanBeDismissed() {
        start()
        controller.onClipboard("hello")
        now += 2 * 60_000
        assertThat(controller.hasLiveClip()).isTrue()
        now += 61_000
        assertThat(controller.strip().clip).isNull()
        assertThat(controller.hasLiveClip()).isFalse()
        controller.onClipboard("again")
        controller.dismissClip()
        assertThat(controller.strip().clip).isNull()
    }

    @Test fun oldClipsFromBeforeAreNotOffered() {
        start()
        controller.onClipboard("stale", copiedAt = now - 10 * 60_000)
        assertThat(controller.strip().clip).isNull()
    }

    @Test fun sensitiveClipsAreMaskedButPaste() {
        val t = start()
        controller.onClipboard("hunter2-Secret", sensitive = true)
        assertThat(controller.strip().clip!!.preview()).isEqualTo("••••••")
        controller.insertClip()
        assertThat(t.value).isEqualTo("hunter2-Secret")
    }

    @Test fun codesStillBecomeTheCodeChipNotText() {
        start()
        controller.onClipboard("Your code is 482913")
        val strip = controller.strip()
        assertThat(strip.code?.code).isEqualTo("482913")
        assertThat(strip.clip).isNull()
    }

    @Test fun copiedTextCanBeTurnedOff() {
        start(settings = KeyboardSettings(showCopiedText = false))
        controller.onClipboard("hello")
        assertThat(controller.strip().clip).isNull()
    }
}
