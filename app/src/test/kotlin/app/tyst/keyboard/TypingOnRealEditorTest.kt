package app.tyst.keyboard

import android.app.Activity
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.TystEngine
import app.tyst.core.field.FieldClassifier
import app.tyst.core.memory.MemoryType
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.typing.TypingController
import app.tyst.keyboard.ime.InputConnectionTarget
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Drives the real typing logic against a real Android EditText through its InputConnection,
 * the same path the keyboard uses on a phone.
 */
@RunWith(AndroidJUnit4::class)
class TypingOnRealEditorTest {
    private lateinit var edit: EditText
    private lateinit var controller: TypingController
    private lateinit var engine: TystEngine
    private val keys = ArrayList<Int>()

    private fun focus(inputType: Int, imeOptions: Int, settings: KeyboardSettings = KeyboardSettings(memoryEnabled = true)) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        edit = EditText(activity).apply {
            this.inputType = inputType
            this.imeOptions = imeOptions
        }
        activity.setContentView(edit)
        edit.requestFocus()
        val info = EditorInfo()
        val ic = edit.onCreateInputConnection(info)!!
        info.packageName = "com.whatsapp"
        val field = FieldClassifier.classify(info.inputType, info.imeOptions, info.packageName)
        val target = InputConnectionTarget({ ic }, object : InputConnectionTarget.KeySender {
            override fun sendDownUpKeyEvents(keyCode: Int) {
                keys += keyCode
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            override fun sendKeyChar(c: Char) { ic.commitText(c.toString(), 1) }
        }, info, field)
        controller = TypingController(engine)
        controller.start(field, target, settings)
    }

    @Before fun setUp() {
        engine = loadedGraph().engine.value!!
    }

    private fun type(s: String) {
        for (c in s) if (c == ' ') controller.onSpace() else controller.onText(c.toString())
    }

    private val text get() = edit.text.toString()

    @Test fun typesCorrectsAndUndoesInAMessageField() {
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEND)
        type("can we meet at Järntorget around teh ")
        assertThat(text).isEqualTo("Can we meet at Järntorget around the ")
        controller.onBackspace()
        assertThat(text).isEqualTo("Can we meet at Järntorget around teh")
    }

    @Test fun suggestionPickReplacesWordInTheEditor() {
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_DONE)
        type("The train to Vasteras")
        val restore = controller.strip().suggestions.first()
        assertThat(restore.text).isEqualTo("Västerås")
        controller.pick(restore)
        assertThat(text).isEqualTo("The train to Västerås ")
    }

    @Test fun memoryChipInsertsIntoTheEditor() {
        engine.memory.add(MemoryType.ADDRESS, "Kungsgatan 4, 411 19 Göteborg", "x", 1)
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEND)
        type("my address is ")
        val chip = controller.strip().memory.single()
        controller.insertMemory(chip)
        assertThat(text).isEqualTo("My address is Kungsgatan 4, 411 19 Göteborg")
    }

    @Test fun codeFieldFillsCopiedCode() {
        focus(InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_DONE)
        controller.onClipboard("Your code is 482913")
        assertThat(controller.insertCode()).isTrue()
        assertThat(text).isEqualTo("482913")
    }

    @Test fun copiedTextPastesIntoTheEditor() {
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEND)
        type("link: ")
        controller.onClipboard("https://example.com/doc")
        assertThat(controller.strip().clip?.preview()).isEqualTo("https://example.com/doc")
        controller.insertClip()
        assertThat(text).isEqualTo("Link: https://example.com/doc")
    }

        @Test fun backspaceOnEmptyFieldSendsAKeyEvent() {
        focus(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE)
        controller.onBackspace()
        assertThat(keys).containsExactly(KeyEvent.KEYCODE_DEL)
    }

    @Test fun writingToolsReplaceTheSelection() {
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0)
        type("hello there friend")
        edit.setSelection(6, 11)
        assertThat(controller.textForTools()).isEqualTo("there")
        assertThat(controller.toolsTextStartsSentence()).isFalse()
        val fixes = engine.proofreader.check(controller.textForTools(), startsSentence = controller.toolsTextStartsSentence())
        assertThat(fixes).isEmpty()
        controller.replaceText("dear")
        assertThat(text).isEqualTo("Hello dear friend")
    }

    @Test fun passwordFieldLearnsNothing() {
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE)
        type("zqxwvpass zqxwvpass ")
        assertThat(text).isEqualTo("zqxwvpass zqxwvpass ")
        assertThat(engine.personal.count("zqxwvpass")).isEqualTo(0)
        assertThat(controller.strip().incognito).isTrue()
    }
}
