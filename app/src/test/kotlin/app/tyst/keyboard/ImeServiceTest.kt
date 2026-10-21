package app.tyst.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.layout.Key
import app.tyst.core.layout.KeyType
import app.tyst.keyboard.ime.KeyboardRoot
import app.tyst.keyboard.ime.TystImeService
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The service starts, builds its views and handles keys even with no field connected. */
@RunWith(AndroidJUnit4::class)
class ImeServiceTest {
    private fun waitForEngine() {
        val g = context().graph
        val until = System.currentTimeMillis() + 20_000
        while (g.engine.value == null && System.currentTimeMillis() < until) {
            Thread.sleep(20)
            idleMain()
        }
        assertThat(g.engine.value).isNotNull()
    }

    @Test fun createsInputViewAndSurvivesKeysWithoutAField() {
        val service = Robolectric.buildService(TystImeService::class.java).create().get()
        waitForEngine()
        val root = service.onCreateInputView()
        assertThat(root).isInstanceOf(KeyboardRoot::class.java)

        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            packageName = "com.example"
        }
        service.onStartInputView(info, false)
        service.onKey(Key(KeyType.CHAR, "a"))
        service.onKey(Key(KeyType.SPACE, " "))
        service.onKey(Key(KeyType.SYMBOLS))
        service.onKey(Key(KeyType.LETTERS))
        service.onBackspace(0)
        service.onBackspace(20)
        service.onFinishInput()
        service.onDestroy()
    }

    @Test fun passwordFieldShowsNoWritingTools() {
        val service = Robolectric.buildService(TystImeService::class.java).create().get()
        waitForEngine()
        val root = service.onCreateInputView() as KeyboardRoot
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }, false)
        idleMain()
        val tools = (0 until root.strip.childCount).map { root.strip.getChildAt(it) }.first { it.contentDescription == "Writing tools" }
        assertThat(tools.visibility).isNotEqualTo(android.view.View.VISIBLE)
        val shield = (0 until root.strip.childCount).map { root.strip.getChildAt(it) }.first { it.contentDescription == "Not learning here" }
        assertThat(shield.visibility).isEqualTo(android.view.View.VISIBLE)
    }
}
