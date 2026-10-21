package app.tyst.keyboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.keyboard.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensOnboardingOrSettings() {
        compose.waitForIdle()
        val onboarding = runCatching { compose.onNodeWithText("Get started").assertExists() }.isSuccess
        if (!onboarding) compose.onNodeWithText("Typing & dictionaries").assertExists()
    }
}
