package app.tyst.keyboard.ime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.PopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import app.tyst.core.correct.TapOffset
import app.tyst.core.field.EnterAction
import app.tyst.core.icons.Icon
import app.tyst.core.icons.Icons
import app.tyst.core.layout.Key
import app.tyst.core.layout.KeyGeometry
import app.tyst.core.layout.KeyType
import app.tyst.core.layout.KeyboardLayout
import app.tyst.core.layout.Layouts
import app.tyst.core.layout.PlacedKey
import app.tyst.core.layout.PopupAction
import app.tyst.core.layout.of
import app.tyst.core.theme.KeyboardColors
import app.tyst.core.theme.Tokens
import app.tyst.core.typing.ShiftState

/**
 * The key plate. Draws keys from a [KeyboardLayout] and turns touches into key events.
 * Sizes follow the design system: 12dp corners (16dp pressed), 6dp/10dp gaps, 4dp side padding.
 */
class KeyboardView(context: Context) : View(context) {
    interface Listener {
        fun onKeyDown(key: Key) {}
        fun onKey(key: Key)
        /** A key was tapped; for letter keys [tap] says where on the key the finger landed. */
        fun onKeyTap(key: Key, tap: TapOffset?) = onKey(key)
        fun onPopupText(text: String)
        fun onPopupAction(action: String)
        /** Backspace held: [step] 0 is the first press, then one per repeat. */
        fun onBackspace(step: Int)
        fun onSpaceLongPress() {}
        /** Finger sliding along the space bar: move the cursor [steps] characters (negative = left). */
        fun onCursorSlide(steps: Int) {}
        /** Finger slid left off backspace: select the last [words] words, from where the slide began. */
        fun onDeleteSlideStart() {}
        fun onDeleteSlide(words: Int) {}
        /** The slide ended: delete the selection ([commit]), or put the cursor back. */
        fun onDeleteSlideEnd(commit: Boolean) {}
    }

    var listener: Listener? = null
    var interactive = true

    var layout: KeyboardLayout = Layouts.letters(numberRow = false, swedishRow = false)
        set(value) { if (field != value) { field = value; dismissPopup(); geometry = null; requestLayout(); invalidate(); a11y.invalidateRoot() } }
    var colors: KeyboardColors = Tokens.resolve(app.tyst.core.theme.ThemeMode.LIGHT, false, app.tyst.core.theme.AccentSource.PALETTE, "hav", 12)
        set(value) { field = value; invalidate() }
    var shift: ShiftState = ShiftState.OFF
        set(value) { if (field != value) { field = value; invalidate(); a11y.invalidateRoot() } }
    var enterAction: EnterAction = EnterAction.NEWLINE
        set(value) { if (field != value) { field = value; invalidate() } }
    var keyHeightDp: Int = 52
        set(value) { if (field != value) { field = value; geometry = null; requestLayout() } }
    var keyBorders = false
        set(value) { field = value; invalidate() }
    var spaceLabel = "EN · SV"
        set(value) { field = value; invalidate() }

    private var geometry: KeyGeometry? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(1f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val radius = context.dp(12f)
    private val radiusPressed = context.dp(16f)
    private val edge = context.dp(1f)

    // ---- touch state ----
    private enum class Slide { NONE, CURSOR, DELETE }

    private class Pointer(val id: Int, val key: PlacedKey, val downX: Float, val downY: Float) {
        var longPressed = false
        var popup: PopupState? = null
        var repeat = 0
        var slide = Slide.NONE
        /** Characters (cursor) or words (delete) reported so far in the current slide. */
        var slideSteps = 0
    }

    private class PopupState(val options: List<String>, val left: Float, val cell: Float, var selected: Int)

    private val pointers = LinkedHashMap<Int, Pointer>()
    private val pressedAt = HashMap<PlacedKey, Long>()
    private val releasedAt = HashMap<PlacedKey, Long>()
    private var popupWindow: PopupWindow? = null
    private var popupView: PopupKeysView? = null

    private val a11y = KeyboardAccessibility()

    init {
        ViewCompat.setAccessibilityDelegate(this, a11y)
        isHapticFeedbackEnabled = true
    }

    private fun geometry(width: Int): KeyGeometry {
        val g = geometry
        if (g != null && g.width == width.toFloat()) return g
        val ng = KeyGeometry(
            layout = layout, width = width.toFloat(), keyHeight = context.dp(keyHeightDp.toFloat()),
            hGap = context.dp(6f), vGap = context.dp(10f), padSides = context.dp(4f), padTop = context.dp(4f), padBottom = context.dp(4f),
        )
        geometry = ng
        return ng
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val g = geometry(if (width > 0) width else resources.displayMetrics.widthPixels)
        setMeasuredDimension(width, g.height.toInt())
    }

    /** Height this view will take at [width] pixels, for panels that replace it. */
    fun heightFor(width: Int): Int = geometry(width).height.toInt()

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        val g = geometry(width)
        val now = SystemClock.uptimeMillis()
        var animating = false
        for (pk in g.keys) {
            val pressed = pressedAt.containsKey(pk)
            var t = if (pressed) 1f else 0f
            releasedAt[pk]?.let { at ->
                val p = (now - at) / RELEASE_MS.toFloat()
                if (p >= 1f || !ValueAnimator.areAnimatorsEnabled()) releasedAt.remove(pk) else { t = 1f - decelerate(p); animating = true }
            }
            drawKey(canvas, pk, t)
        }
        if (animating) postInvalidateOnAnimation()
    }

    private fun decelerate(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

    private fun drawKey(canvas: Canvas, pk: PlacedKey, pressT: Float) {
        val n = colors.n
        val a = colors.accent
        val key = pk.key
        val (bg, fg) = keyColors(key)
        val v = pk.visual
        val scale = 1f - 0.04f * pressT
        val cx = v.centerX
        val cy = v.centerY
        rect.set(cx - v.width / 2 * scale, cy - v.height / 2 * scale, cx + v.width / 2 * scale, cy + v.height / 2 * scale)
        val r = radius + (radiusPressed - radius) * pressT

        if (pressT < 1f && bg != 0) {
            fill.color = withAlpha(n.keyEdge, if (colors.dark) 0.9f else 0.55f)
            rect.offset(0f, edge)
            canvas.drawRoundRect(rect, r, r, fill)
            rect.offset(0f, -edge)
        }
        fill.color = if (pressT > 0f && key.type != KeyType.ENTER) blend(bg, n.keyPressed, pressT)
        else if (pressT > 0f) blend(bg, a.onAccent, 0.18f * pressT) else bg
        canvas.drawRoundRect(rect, r, r, fill)
        if (keyBorders) {
            stroke.color = n.outline
            canvas.drawRoundRect(rect, r, r, stroke)
        }

        val icon = iconFor(key)
        if (icon != null) {
            IconPainter.draw(canvas, icon, cx, cy, context.dp(24f), fg)
            return
        }
        val label = labelFor(key)
        if (label.isEmpty()) return
        val isLetter = key.type == KeyType.CHAR && label.length == 1 && label[0].isLetter()
        text.color = if (key.type == KeyType.SPACE) n.textSecondary else fg
        text.typeface = Type.medium
        text.textSize = when {
            key.type == KeyType.SPACE -> context.sp(14f)
            key.type != KeyType.CHAR -> context.sp(14f)
            label.length > 1 -> context.sp(16f)
            isLetter || label[0].isDigit() -> context.sp(22f)
            else -> context.sp(20f)
        }
        val baseline = cy - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, cx, baseline, text)
    }

    private fun keyColors(key: Key): Pair<Int, Int> {
        val n = colors.n
        val a = colors.accent
        return when (key.type) {
            KeyType.ENTER -> a.accent to a.onAccent
            KeyType.SHIFT -> when (shift) {
                ShiftState.OFF -> n.special to n.textPrimary
                ShiftState.ON -> a.container to a.onContainer
                ShiftState.LOCKED -> a.accent to a.onAccent
            }
            KeyType.CHAR -> if (key.text == "," || key.text == ".") n.special to n.textPrimary else n.key to n.textPrimary
            KeyType.SPACE -> n.space to n.textSecondary
            KeyType.SPACER -> 0 to 0
            else -> n.special to n.textPrimary
        }
    }

    private fun labelFor(key: Key): String = when (key.type) {
        KeyType.SPACE -> spaceLabel
        KeyType.CHAR -> if (shift != ShiftState.OFF && key.text.length == 1 && key.text[0].isLetter()) key.text.uppercase() else key.label
        else -> key.label
    }

    private fun iconFor(key: Key): Icon? = when (key.type) {
        KeyType.SHIFT -> when (shift) {
            ShiftState.OFF -> Icons.SHIFT
            ShiftState.ON -> Icons.SHIFT_ON
            ShiftState.LOCKED -> Icons.CAPS_LOCK
        }
        KeyType.BACKSPACE -> Icons.BACKSPACE
        KeyType.ENTER -> when (enterAction) {
            EnterAction.SEND -> Icons.SEND
            EnterAction.SEARCH -> Icons.SEARCH
            EnterAction.DONE -> Icons.CHECK
            EnterAction.GO, EnterAction.NEXT -> Icons.ARROW_FORWARD
            EnterAction.PREVIOUS -> Icons.ARROW_BACK
            EnterAction.NEWLINE -> Icons.RETURN
        }
        KeyType.EMOJI -> Icons.EMOJI
        else -> null
    }

    // ---- touch ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        val g = geometry ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pk = g.keyAt(event.getX(idx), event.getY(idx)) ?: return true
                // Rollover: a new finger lands while another is down, so the earlier key is typed now.
                for (p in pointers.values.toList()) {
                    if (p.popup == null && !p.longPressed && p.slide == Slide.NONE && p.key.key.type != KeyType.BACKSPACE && p.key.key.type != KeyType.SHIFT) release(p, commit = true)
                }
                val p = Pointer(event.getPointerId(idx), pk, event.getX(idx), event.getY(idx))
                pointers[p.id] = p
                pressedAt[pk] = SystemClock.uptimeMillis()
                releasedAt.remove(pk)
                listener?.onKeyDown(pk.key)
                if (pk.key.type == KeyType.BACKSPACE) {
                    listener?.onBackspace(0)
                    postDelayed({ repeatBackspace(p) }, REPEAT_START_MS)
                } else if (pk.key.popup.isNotEmpty() || pk.key.type == KeyType.SPACE) {
                    postDelayed({ longPress(p) }, LONG_PRESS_MS)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = pointers[event.getPointerId(i)] ?: continue
                    if (p.popup == null) {
                        slide(p, event.getX(i))
                        continue
                    }
                    val popup = p.popup ?: continue
                    val sel = ((event.getX(i) - popup.left) / popup.cell).toInt().coerceIn(0, popup.options.size - 1)
                    if (sel != popup.selected) {
                        popup.selected = sel
                        popupView?.selected = sel
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val p = pointers[event.getPointerId(event.actionIndex)]
                if (p != null) release(p, commit = true)
            }
            MotionEvent.ACTION_CANCEL -> {
                pointers.values.toList().forEach { release(it, commit = false) }
            }
        }
        return true
    }

    /**
     * Space and backspace turn into slides once the finger moves sideways past [slideSlop]: along space
     * it moves the cursor, left from backspace it selects whole words to delete.
     */
    private fun slide(p: Pointer, x: Float) {
        val type = p.key.key.type
        if (p.longPressed && p.slide == Slide.NONE) return
        val dx = x - p.downX
        val slop = context.dp(SLIDE_SLOP_DP)
        if (p.slide == Slide.NONE) {
            when {
                type == KeyType.SPACE && kotlin.math.abs(dx) > slop -> p.slide = Slide.CURSOR
                type == KeyType.BACKSPACE && dx < -slop -> {
                    p.slide = Slide.DELETE
                    listener?.onDeleteSlideStart()
                }
                else -> return
            }
        }
        when (p.slide) {
            Slide.CURSOR -> {
                // Start counting from the slop edge so the cursor doesn't jump when the slide begins.
                val travel = if (dx > 0) dx - slop else dx + slop
                val steps = (travel / context.dp(CURSOR_STEP_DP)).toInt()
                if (steps != p.slideSteps) {
                    listener?.onCursorSlide(steps - p.slideSteps)
                    p.slideSteps = steps
                }
            }
            Slide.DELETE -> {
                val words = if (dx >= -slop) 0 else 1 + ((-dx - slop) / context.dp(DELETE_STEP_DP)).toInt()
                if (words != p.slideSteps) {
                    listener?.onDeleteSlide(words)
                    p.slideSteps = words
                }
            }
            Slide.NONE -> Unit
        }
    }

    private fun release(p: Pointer, commit: Boolean) {
        if (pointers.remove(p.id) == null) return
        pressedAt.remove(p.key)
        releasedAt[p.key] = SystemClock.uptimeMillis()
        when (p.slide) {
            Slide.CURSOR -> { invalidate(); return }
            Slide.DELETE -> { listener?.onDeleteSlideEnd(commit); invalidate(); return }
            Slide.NONE -> Unit
        }
        val popup = p.popup
        if (popup != null) {
            dismissPopup()
            if (commit) {
                val choice = popup.options[popup.selected]
                if (PopupAction.isAction(choice)) listener?.onPopupAction(choice) else listener?.onPopupText(caseFor(choice))
            }
        } else if (commit && !p.longPressed && p.key.key.type != KeyType.BACKSPACE) {
            listener?.onKeyTap(p.key.key, tapFor(p))
        }
        invalidate()
    }

    /** Where the finger first landed on a letter key; that is where it was aiming. */
    private fun tapFor(p: Pointer): TapOffset? {
        val key = p.key.key
        if (key.type != KeyType.CHAR || key.text.length != 1 || !key.text[0].isLetter()) return null
        return TapOffset.of(p.key, p.downX, p.downY)
    }

    private fun caseFor(s: String) = if (shift != ShiftState.OFF && s.length == 1 && s[0].isLetter()) s.uppercase() else s

    private fun repeatBackspace(p: Pointer) {
        if (pointers[p.id] !== p || p.slide != Slide.NONE) return
        p.repeat++
        listener?.onBackspace(p.repeat)
        postDelayed({ repeatBackspace(p) }, REPEAT_EVERY_MS)
    }

    private fun longPress(p: Pointer) {
        if (pointers[p.id] !== p || p.popup != null || p.slide != Slide.NONE) return
        p.longPressed = true
        if (p.key.key.type == KeyType.SPACE) {
            listener?.onSpaceLongPress()
            return
        }
        showPopup(p)
    }

    private fun showPopup(p: Pointer) {
        val options = p.key.key.popup.map { caseFor(it) }
        val cell = context.dp(40f)
        val h = context.dp(52f)
        val pad = context.dp(6f)
        val width = cell * options.size + 2 * pad
        val left = (p.key.visual.centerX - cell / 2 - pad).coerceIn(0f, maxOf(0f, this.width - width))
        val top = p.key.visual.top - h - 2 * pad - context.dp(4f)
        val state = PopupState(options, left + pad, cell, 0)
        p.popup = state
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        val view = PopupKeysView(context, options, colors, cell, h, pad)
        popupView = view
        val window = PopupWindow(view, width.toInt(), (h + 2 * pad).toInt(), false).apply {
            isClippingEnabled = false
            isTouchable = false
            elevation = context.dp(6f)
            setBackgroundDrawable(null)
        }
        val loc = IntArray(2)
        getLocationInWindow(loc)
        try {
            window.showAtLocation(this, Gravity.NO_GRAVITY, loc[0] + left.toInt(), loc[1] + top.toInt())
            popupWindow = window
        } catch (_: RuntimeException) {
            // No window token yet (the keyboard is closing); the long-press is simply ignored.
            p.popup = null
        }
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
    }

    override fun onDetachedFromWindow() {
        dismissPopup()
        pointers.clear()
        pressedAt.clear()
        super.onDetachedFromWindow()
    }

    // ---- accessibility ----

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    fun describe(key: Key): String = when (key.type) {
        KeyType.SHIFT -> when (shift) {
            ShiftState.OFF -> "Shift"
            ShiftState.ON -> "Shift on"
            ShiftState.LOCKED -> "Caps lock on"
        }
        KeyType.BACKSPACE -> "Delete"
        KeyType.ENTER -> when (enterAction) {
            EnterAction.SEND -> "Send"
            EnterAction.SEARCH -> "Search"
            EnterAction.DONE -> "Done"
            EnterAction.GO -> "Go"
            EnterAction.NEXT -> "Next"
            EnterAction.PREVIOUS -> "Previous"
            EnterAction.NEWLINE -> "New line"
        }
        KeyType.SPACE -> "Space"
        KeyType.SYMBOLS -> "Symbols"
        KeyType.SYMBOLS_MORE -> "More symbols"
        KeyType.LETTERS -> "Letters"
        KeyType.EMOJI -> "Emoji"
        KeyType.SPACER -> ""
        KeyType.CHAR -> {
            val base = when (key.text) {
                "," -> "Comma"
                "." -> "Period"
                else -> labelFor(key)
            }
            val alts = key.popup.filterNot { PopupAction.isAction(it) || it.firstOrNull()?.isDigit() == true }
            when {
                key.text == "," -> "$base. Hold for emoji and settings"
                alts.isNotEmpty() && alts.first() in listOf("å", "ö") -> "$base. Hold for ${alts.takeWhile { it in listOf("å", "ä", "ö") }.joinToString(", ")}"
                else -> base
            }
        }
    }

    private inner class KeyboardAccessibility : ExploreByTouchHelper(this@KeyboardView) {
        private val keys get() = geometry?.keys ?: emptyList()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val pk = geometry?.keyAt(x, y) ?: return INVALID_ID
            return keys.indexOf(pk).takeIf { it >= 0 } ?: INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            keys.indices.forEach { virtualViewIds += it }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val pk = keys.getOrNull(virtualViewId)
            if (pk == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.contentDescription = describe(pk.key)
            node.className = "android.inputmethodservice.Keyboard\$Key"
            node.setBoundsInParent(Rect(pk.slot.left.toInt(), pk.slot.top.toInt(), pk.slot.right.toInt(), pk.slot.bottom.toInt()))
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (pk.key.popup.isNotEmpty()) node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val pk = keys.getOrNull(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    if (pk.key.type == KeyType.BACKSPACE) listener?.onBackspace(0) else listener?.onKey(pk.key)
                    true
                }
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                    // TalkBack: type the first alternative (å on "a").
                    val first = pk.key.popup.firstOrNull() ?: return false
                    if (PopupAction.isAction(first)) listener?.onPopupAction(first) else listener?.onPopupText(caseFor(first))
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        const val LONG_PRESS_MS = 300L
        const val REPEAT_START_MS = 400L
        const val REPEAT_EVERY_MS = 55L
        const val RELEASE_MS = 150L
        /** Sideways travel before space or backspace becomes a slide. */
        const val SLIDE_SLOP_DP = 14f
        /** Space-bar travel per character of cursor movement. */
        const val CURSOR_STEP_DP = 12f
        /** Backspace-slide travel per word. */
        const val DELETE_STEP_DP = 28f
    }
}

/** The long-press popup: one 40dp cell per option, the selected one in the accent. */
class PopupKeysView(
    context: Context,
    private val options: List<String>,
    private val colors: KeyboardColors,
    private val cell: Float,
    private val cellHeight: Float,
    private val pad: Float,
) : View(context) {
    var selected = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Type.medium; textSize = context.sp(24f) }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        paint.color = colors.n.popup
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = context.dp(20f)
        canvas.drawRoundRect(rect, r, r, paint)
        options.forEachIndexed { i, opt ->
            val left = pad + i * cell
            val sel = i == selected
            if (sel) {
                paint.color = colors.accent.accent
                rect.set(left, pad, left + cell, pad + cellHeight)
                canvas.drawRoundRect(rect, context.dp(12f), context.dp(12f), paint)
            }
            val fg = if (sel) colors.accent.onAccent else colors.n.textPrimary
            val cx = left + cell / 2
            val cy = pad + cellHeight / 2
            val icon = when (opt) {
                PopupAction.EMOJI -> Icons.EMOJI
                PopupAction.SETTINGS -> Icons.SETTINGS
                PopupAction.ONE_HANDED -> Icons.CHEVRON_RIGHT
                else -> null
            }
            if (icon != null) {
                IconPainter.draw(canvas, icon, cx, cy, context.dp(22f), fg)
            } else {
                text.color = fg
                canvas.drawText(opt, cx, cy - (text.descent() + text.ascent()) / 2, text)
            }
        }
    }
}
