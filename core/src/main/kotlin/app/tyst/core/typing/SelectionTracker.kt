package app.tyst.core.typing

/**
 * Tells the keyboard's own edits apart from the user moving the cursor (or the app changing the text).
 *
 * After each edit the keyboard predicts where the selection will land; Android then reports selection
 * updates. An update that matches a prediction is ours. Anything else is treated as a cursor move, which
 * only makes the keyboard forget pending state (autocorrect undo, the automatic space). A wrong guess is
 * therefore harmless, unlike counting updates, where one missing update made the next real tap invisible.
 *
 * Positions are UTF-16 offsets, as Android reports them; -1 means unknown.
 */
class SelectionTracker {
    var start = -1
        private set
    var end = -1
        private set
    private val expected = ArrayDeque<Long>()
    private var depth = 0
    private var dirtyInBatch = false

    val known: Boolean get() = start >= 0 && end >= 0

    /** A new field: [selStart]/[selEnd] from EditorInfo.initialSelStart/End (may be -1). */
    fun reset(selStart: Int, selEnd: Int) {
        start = minOf(selStart, selEnd)
        end = maxOf(selStart, selEnd)
        expected.clear()
        depth = 0
        dirtyInBatch = false
    }

    /** commitText([length] chars): replaces the selection and puts the cursor after the new text. */
    fun committed(length: Int) {
        if (!known) return
        start += length
        end = start
        edited()
    }

    /** deleteSurroundingText([before], [after]): the selection itself is kept, shifted left. */
    fun deleted(before: Int, after: Int) {
        if (!known) return
        val removed = minOf(before, start)
        start -= removed
        end -= removed
        edited()
    }

    /** An edit whose effect can't be predicted (raw key events): the next update can't be matched. */
    fun unpredictable() {
        start = -1
        end = -1
        expected.clear()
    }

    fun beginBatch() { depth++ }

    fun endBatch() {
        if (depth == 0) return
        depth--
        if (depth == 0 && dirtyInBatch) {
            dirtyInBatch = false
            push()
        }
    }

    /**
     * A selection update from Android. Returns true when it was not caused by the keyboard's own edits,
     * i.e. the user moved the cursor or the text changed outside the keyboard.
     */
    fun onUpdate(newStart: Int, newEnd: Int): Boolean {
        val s = minOf(newStart, newEnd)
        val e = maxOf(newStart, newEnd)
        val key = pack(s, e)
        val wasKnown = known
        val unchanged = wasKnown && s == start && e == end && expected.isEmpty()
        val index = expected.indexOf(key)
        val ours = index >= 0
        if (ours) {
            // Editors may skip intermediate updates; everything up to this one is accounted for.
            repeat(index + 1) { expected.removeFirst() }
        } else {
            expected.clear()
        }
        if (!ours || expected.isEmpty()) {
            start = s
            end = e
        }
        return !(ours || unchanged)
    }

    private fun edited() {
        if (depth > 0) dirtyInBatch = true else push()
    }

    private fun push() {
        expected.addLast(pack(start, end))
        // Updates can be dropped entirely; never let stale guesses pile up.
        while (expected.size > MAX_PENDING) expected.removeFirst()
    }

    private fun pack(s: Int, e: Int): Long = (s.toLong() shl 32) or (e.toLong() and 0xffffffffL)

    private companion object {
        const val MAX_PENDING = 16
    }
}
