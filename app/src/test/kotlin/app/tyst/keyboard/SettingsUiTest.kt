package app.tyst.keyboard

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.memory.MemoryType
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode
import app.tyst.keyboard.data.Graph
import app.tyst.keyboard.data.SettingsRepository
import app.tyst.keyboard.ui.TystRoot
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var graph: Graph

    @Before fun setUp() {
        context().getSharedPreferences(SettingsRepository.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        File(context().filesDir, "learned").deleteRecursively()
        graph = loadedGraph()
    }

    private fun show(onboarded: Boolean = true) {
        graph.settings.update { it.copy(onboardingDone = onboarded) }
        compose.setContent { TystRoot(graph, 0) }
    }

    @Test fun onboardingWalksThroughFiveSteps() {
        show(onboarded = false)
        compose.onNodeWithText("A keyboard that learns how you write and never goes online.").assertIsDisplayed()
        compose.onNodeWithText("Not requested").assertIsDisplayed()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Turn on Tyst").assertIsDisplayed()
        compose.onNodeWithText("2 of 5").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Everything you type stays on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Private memory").performClick()
        assertThat(graph.settings.current.memoryEnabled).isTrue()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Pick a look").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        assertThat(graph.settings.current.themeMode).isEqualTo(ThemeMode.DARK)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("You’re set").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        assertThat(graph.settings.current.onboardingDone).isTrue()
        compose.onNodeWithText("Typing & dictionaries").assertIsDisplayed()
    }

    @Test fun typingSettingsToggleSwedishWords() {
        show()
        compose.onNodeWithText("Typing & dictionaries").performClick()
        compose.onNodeWithText("Swedish words").performClick()
        assertThat(graph.settings.current.swedishWords).isFalse()
        compose.onNodeWithText("Turn on Swedish words to use this.").assertIsDisplayed()
        compose.onNodeWithText("Strong").performClick()
        assertThat(graph.settings.current.autocorrect.name).isEqualTo("STRONG")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("English · Swedish words off").assertIsDisplayed()
    }

    @Test fun memoryDeleteCanBeUndone() {
        graph.engine.value!!.memory.add(MemoryType.BOOKING, "ABX42K", "com.google.android.gm", 1_700_000_000_000L)
        show()
        compose.onNodeWithText("Privacy & memory").performClick()
        compose.onNodeWithText("ABX42K").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete booking reference").performClick()
        compose.waitForIdle()
        assertThat(graph.engine.value!!.memory.size).isEqualTo(0)
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertThat(graph.engine.value!!.memory.size).isEqualTo(1)
    }

    @Test fun searchFiltersMemory() {
        val m = graph.engine.value!!.memory
        m.add(MemoryType.BOOKING, "ABX42K", "x", 1)
        m.add(MemoryType.EMAIL, "elin.berg@hey.com", "x", 2)
        show()
        compose.onNodeWithText("Privacy & memory").performClick()
        compose.onNodeWithText("Search what’s remembered").performTextInput("abx")
        compose.onNodeWithText("ABX42K").assertIsDisplayed()
        compose.onAllNodesWithText("elin.berg@hey.com").assertCountEquals(0)
    }

    @Test fun deleteAllNeedsConfirmation() {
        val e = graph.engine.value!!
        e.memory.add(MemoryType.EMAIL, "elin.berg@hey.com", "x", 1)
        e.personal.addWord("Järntorget", "x", 1)
        show()
        compose.onNodeWithText("Privacy & memory").performClick()
        compose.onNodeWithText("Delete all learned data").performClick()
        compose.onNodeWithText("Delete everything Tyst has learned?").assertIsDisplayed()
        compose.onNodeWithText("Delete all").performClick()
        compose.waitForIdle()
        assertThat(e.memory.size).isEqualTo(0)
        assertThat(e.personal.dictionaryWords).isEmpty()
    }

    @Test fun appearancePicksAPalette() {
        show()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithContentDescription("Lingon, lingonberry").performClick()
        assertThat(graph.settings.current.accentSource).isEqualTo(AccentSource.PALETTE)
        assertThat(graph.settings.current.paletteId).isEqualTo("lingon")
        compose.onNodeWithText("XL").performClick()
        assertThat(graph.settings.current.keyHeightDp).isEqualTo(60)
        compose.onNodeWithContentDescription("Keyboard preview").assertExists()
    }

    @Test fun privacyProofReadsPermissionsLive() {
        show()
        compose.onNodeWithText("Privacy proof").performClick()
        compose.onNodeWithText("Not requested. Tyst can’t reach the network.").assertIsDisplayed()
        compose.onAllNodesWithText("Microphone").onFirst().assertIsDisplayed()
    }

    @Test fun wordsCanBeAddedAndRemoved() {
        show()
        compose.onNodeWithText("Typing & dictionaries").performClick()
        compose.onNodeWithText("Personal dictionary").performClick()
        compose.onNodeWithText("Add a word").performTextInput("Tysta")
        compose.onAllNodesWithText("Add").onFirst().performClick()
        assertThat(graph.engine.value!!.personal.inDictionary("tysta")).isTrue()
        compose.onNodeWithContentDescription("Remove Tysta").performClick()
        assertThat(graph.engine.value!!.personal.inDictionary("tysta")).isFalse()
    }

    @Test fun passwordsScreenExplainsWhatTystNeverSees() {
        show()
        compose.onNodeWithText("Passwords & codes").performClick()
        compose.onNodeWithText("3 min").performClick()
        assertThat(graph.settings.current.codeExpirySeconds).isEqualTo(180)
        compose.onNodeWithText("None chosen · pick one in Android settings").assertIsDisplayed()
    }
}
