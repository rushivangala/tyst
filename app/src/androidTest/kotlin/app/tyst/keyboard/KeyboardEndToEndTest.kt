package app.tyst.keyboard

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real keyboard on a real Android image: enable Tyst, focus a field, tap keys through the
 * accessibility nodes the keyboard exposes, and check what lands in the field.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardEndToEndTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val ime = "app.tyst.keyboard/.ime.TystImeService"
    private var previousIme: String = ""

    @Before fun enableTyst() {
        previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
        device.executeShellCommand("ime enable $ime")
        device.executeShellCommand("ime set $ime")
    }

    @After fun restore() {
        if (previousIme.isNotEmpty() && previousIme != "null") device.executeShellCommand("ime set $previousIme")
    }

    /** The word lists load in the background on first start; typing before that skips autocorrect. */
    private fun waitForEngine() {
        val graph = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TystApp).graph
        val until = System.currentTimeMillis() + 20_000
        while (graph.engine.value == null && System.currentTimeMillis() < until) Thread.sleep(100)
        Thread.sleep(500)
    }

    private fun tapKey(desc: String) {
        // Keys with long-press letters read "o. Hold for ö", so match the start.
        val pattern = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(desc) + "(\\..*)?")
        val key = device.wait(Until.findObject(By.desc(pattern)), 5_000) ?: error("No key \"$desc\" on screen")
        key.click()
        device.waitForIdle()
    }

    private fun fieldText(scenario: ActivityScenario<TestEditorActivity>): String {
        var text = ""
        scenario.onActivity { text = it.field.text.toString() }
        return text
    }

    @Test fun typesAutocorrectsAndUndoes() {
        ActivityScenario.launch(TestEditorActivity::class.java).use { scenario ->
            assertThat(device.wait(Until.hasObject(By.desc("Delete")), 10_000)).isTrue()
            waitForEngine()
            for (k in listOf("H", "i", "Space", "t", "e", "h", "Space")) tapKey(k)
            assertThat(fieldText(scenario)).isEqualTo("Hi the ")
            tapKey("Delete")
            assertThat(fieldText(scenario)).isEqualTo("Hi teh")
        }
    }

    @Test fun suggestionStripOffersWords() {
        ActivityScenario.launch(TestEditorActivity::class.java).use { scenario ->
            // Anything copied earlier on the device would take the strip's place after a space.
            scenario.onActivity { it.getSystemService(android.content.ClipboardManager::class.java)?.clearPrimaryClip() }
            assertThat(device.wait(Until.hasObject(By.desc("Delete")), 10_000)).isTrue()
            waitForEngine()
            for (k in listOf("S", "e", "e", "Space", "y", "o", "u", "Space")) tapKey(k)
            val suggestion = listOf("soon", "later", "at", "tomorrow").firstNotNullOfOrNull { w ->
                device.wait(Until.findObject(By.desc(w)), 2_000)?.let { w to it }
            } ?: error("No next-word suggestion shown. Keyboard shows: " +
                device.findObjects(By.pkg("app.tyst.keyboard")).mapNotNull { it.contentDescription }.filter { it.length > 1 }.distinct())
            suggestion.second.click()
            device.waitForIdle()
            assertThat(fieldText(scenario)).isEqualTo("See you ${suggestion.first} ")
        }
    }
}
