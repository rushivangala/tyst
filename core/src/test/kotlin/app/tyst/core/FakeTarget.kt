package app.tyst.core

import app.tyst.core.field.EnterAction
import app.tyst.core.typing.TextTarget

/** An in-memory editor with a cursor and selection, standing in for Android's InputConnection. */
class FakeTarget(initial: String = "") : TextTarget {
    val text = StringBuilder(initial)
    var cursor = initial.length
    var selStart = cursor
    val actions = ArrayList<EnterAction>()
    var rawBackspaces = 0

    val value: String get() = text.toString()

    override fun before(n: Int) = text.substring(maxOf(0, selStart - n), selStart)
    override fun after(n: Int) = text.substring(cursor, minOf(text.length, cursor + n))
    override fun selected() = text.substring(selStart, cursor)
    override fun commit(text: String) {
        this.text.replace(selStart, cursor, text)
        cursor = selStart + text.length
        selStart = cursor
    }
    override fun deleteAround(before: Int, after: Int) {
        val s = maxOf(0, selStart - before)
        val e = minOf(text.length, cursor + after)
        text.delete(cursor, e)
        text.delete(s, selStart)
        cursor = s
        selStart = s
    }
    override fun sendBackspaceKey() { rawBackspaces++ }
    override fun enter(action: EnterAction) {
        if (action == EnterAction.NEWLINE) commit("\n") else actions += action
    }

    fun select(start: Int, end: Int) { selStart = start; cursor = end }

    /** Set to false to act like an editor that doesn't report offsets (arrow keys only). */
    var reportsOffsets = true
    var arrowKeyPresses = 0

    override fun selection(): Pair<Int, Int>? = if (reportsOffsets) selStart to cursor else null
    override fun setSelection(start: Int, end: Int): Boolean {
        if (!reportsOffsets) return false
        selStart = start.coerceIn(0, text.length)
        cursor = end.coerceIn(selStart, text.length)
        return true
    }
    override fun arrowKeys(chars: Int) {
        arrowKeyPresses += kotlin.math.abs(chars)
        val to = (cursor + chars).coerceIn(0, text.length)
        selStart = to; cursor = to
    }
}
