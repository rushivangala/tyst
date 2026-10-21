package app.tyst.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.tyst.core.field.EnterAction
import app.tyst.core.field.FieldInfo
import app.tyst.core.typing.TextTarget

/**
 * [TextTarget] over the focused field's InputConnection. Counts its own edits so that
 * selection updates caused by typing can be told apart from the user moving the cursor.
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

    /** Selection updates still expected from our own edits. */
    var pendingUpdates = 0
    private var depth = 0
    private var editedInBatch = false

    private val ic get() = connection()
    private val rawKeys = editor.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

    override fun before(n: Int): String = ic?.getTextBeforeCursor(n, 0)?.toString() ?: ""
    override fun after(n: Int): String = ic?.getTextAfterCursor(n, 0)?.toString() ?: ""
    override fun selected(): String = ic?.getSelectedText(0)?.toString() ?: ""

    override fun commit(text: String) = edit {
        if (rawKeys) {
            // Terminals and custom views: plain key events.
            for (c in text) if (c == '\n') keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else keys.sendKeyChar(c)
        } else {
            ic?.commitText(text, 1)
        }
    }

    override fun deleteAround(before: Int, after: Int) = edit {
        if (rawKeys) repeat(before) { keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        else ic?.deleteSurroundingText(before, after)
    }

    override fun sendBackspaceKey() = edit { keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }

    override fun enter(action: EnterAction) = edit {
        when (action) {
            EnterAction.NEWLINE ->
                if (field.multiLine && !rawKeys) ic?.commitText("\n", 1) else keys.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            else -> ic?.performEditorAction(editor.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }

    override fun batch(block: () -> Unit) {
        val c = ic
        c?.beginBatchEdit()
        depth++
        try {
            block()
        } finally {
            depth--
            c?.endBatchEdit()
            if (depth == 0 && editedInBatch) {
                editedInBatch = false
                pendingUpdates++
            }
        }
    }

    private inline fun edit(op: () -> Unit) {
        op()
        if (depth == 0) pendingUpdates++ else editedInBatch = true
    }
}
