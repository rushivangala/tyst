package app.tyst.keyboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout

/** A plain message field for end-to-end keyboard tests. Debug builds only. */
class TestEditorActivity : Activity() {
    lateinit var field: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            contentDescription = "Message"
        }
        setContentView(LinearLayout(this).apply { addView(field) })
        field.requestFocus()
    }
}
