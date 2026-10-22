package app.tyst.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.tyst.core.field.EnterAction
import app.tyst.core.field.FieldInfo
import app.tyst.core.typing.SelectionTracker
import app.tyst.core.typing.TextTarget

/**
 * [TextTarget] over the focused field's InputConnection. Predicts where each of its own edits leaves
 * the cursor ([selection]) so selection updates caused by typing can be told apart from the user
 * moving the cursor.
 */
class InputConnectionTarget(
    private val connection: () -> InputConnection?,
    private val keys: KeySender,
    private val editor: EditorInfo,
    private val field: FieldInfo,
) : TextTarget {
    /** Sends raw key events; the IME service implements this. */
    interface KeySender {
        fun sendDownUpKeyEvents(keyCode: Int)
        fun sendKeyChar(c: Char)
    }

    val selection = SelectionTracker().apply { reset(editor.initialSelStart, editor.initialSelEnd) }

    private val ic get() = connection()
    private val rawKeys = editor.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

    override fun before(n: Int): String = ic?.getTextBeforeCursor(n, 0)?.toString() ?: ""
    override fun after(n: Int): String = ic?.getTextAfterCursor(n, 0)?.toString() ?: ""
    override fun selected(): String = ic?.getSelectedText(0)?.toString() ?: ""

    override fun commit(text: String) {
        if (rawKeys) {
            // Terminals and custom views: plain key events.
            for (c in text) if (c == '\n') keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else keys.sendKeyChar(c)
            selection.unpredictable()
        } else if (ic?.commitText(text, 1) == true) {
            selection.committed(text.length)
        }
    }

    override fun deleteAround(before: Int, after: Int) {
        if (rawKeys) {
            repeat(before) { keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
            selection.unpredictable()
        } else if (ic?.deleteSurroundingText(before, after) == true) {
            selection.deleted(before, after)
        }
    }

    override fun sendBackspaceKey() {
        // Only used where there is nothing before the cursor; if the editor reacts, it shows up as a move.
        keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun enter(action: EnterAction) {
        when (action) {
            EnterAction.NEWLINE ->
                if (field.multiLine && !rawKeys) commit("\n") else {
                    keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    selection.unpredictable()
                }
            else -> ic?.performEditorAction(editor.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }

    override fun batch(block: () -> Unit) {
        val c = ic
        c?.beginBatchEdit()
        selection.beginBatch()
        try {
            block()
        } finally {
            selection.endBatch()
            c?.endBatchEdit()
        }
    }
}
