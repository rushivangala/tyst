package app.tyst.keyboard

import android.app.Activity
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.codes.CopiedCode
import app.tyst.core.memory.MemoryItem
import app.tyst.core.predict.Suggestion
import app.tyst.core.predict.SuggestionKind
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode
import app.tyst.core.theme.Tokens
import app.tyst.core.typing.StripState
import app.tyst.keyboard.ime.StripView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Autofill pills are live system views: they must stay attached across redraws. */
@RunWith(AndroidJUnit4::class)
class StripViewTest {
    private val listener = object : StripView.Listener {
        override fun onSuggestion(s: Suggestion) {}
        override fun onUndoCorrection() {}
        override fun onMemory(item: MemoryItem) {}
        override fun onCode() {}
        override fun onClip() {}
        override fun onDismissClip() {}
        override fun onWritingTools() {}
        override fun onVoice() {}
    }

    private fun strip(): Pair<Activity, StripView> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val strip = StripView(activity, listener)
        activity.setContentView(strip)
        strip.applyColors(Tokens.resolve(ThemeMode.LIGHT, false, AccentSource.PALETTE, "hav", 12))
        return activity to strip
    }

    @Test fun pillsStayAttachedWhileTyping() {
        val (activity, strip) = strip()
        val pill = View(activity)
        strip.setInlineSuggestions(listOf(pill))
        val parent = pill.parent
        assertThat(parent).isNotNull()
        repeat(5) { i ->
            strip.render(StripState(suggestions = listOf(Suggestion("word$i", SuggestionKind.COMPLETION))), voiceAvailable = false, toolsOpen = false)
        }
        assertThat(pill.parent).isSameInstanceAs(parent)
        assertThat((pill.parent as View).isShown).isTrue()
    }

    @Test fun pillsAppearEvenIfTheyArriveFirstAndClearOnEmpty() {
        val (activity, strip) = strip()
        val pill = View(activity)
        strip.setInlineSuggestions(listOf(pill))
        strip.render(StripState(), voiceAvailable = false, toolsOpen = false)
        assertThat((pill.parent as View).isShown).isTrue()
        strip.setInlineSuggestions(emptyList())
        assertThat(pill.parent).isNull()
        assertThat(strip.inlineViews).isEmpty()
    }

    @Test fun aCopiedCodeTakesPriorityOverPills() {
        val (activity, strip) = strip()
        val pill = View(activity)
        strip.setInlineSuggestions(listOf(pill))
        strip.render(StripState(code = CopiedCode("482913", 0), codeSecondsLeft = 30), voiceAvailable = false, toolsOpen = false)
        assertThat((pill.parent as View).isShown).isFalse()
        strip.render(StripState(), voiceAvailable = false, toolsOpen = false)
        assertThat((pill.parent as View).isShown).isTrue()
    }
}
