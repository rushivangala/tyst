package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.tyst.core.codes.OtpDetector
import app.tyst.core.icons.Icons
import app.tyst.core.memory.MemoryItem
import app.tyst.core.predict.Suggestion
import app.tyst.core.predict.SuggestionKind
import app.tyst.core.theme.KeyboardColors
import app.tyst.core.typing.StripState

/**
 * The 48dp suggestion strip: writing-tools key on the left, three suggestion slots (most likely in the
 * centre), and the voice key on the right. Rich chips take the slots' place by priority:
 * copied code > autofill pills > private memory > suggestions.
 */
@SuppressLint("ViewConstructor")
class StripView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onSuggestion(s: Suggestion)
        fun onUndoCorrection()
        fun onMemory(item: MemoryItem)
        fun onCode()
        fun onClip()
        fun onDismissClip()
        fun onWritingTools()
        fun onVoice()
    }

    private var colors: KeyboardColors? = null
    private val tools = IconButton(context, Icons.SPARKLE, "Writing tools").apply { setOnClickListener { listener.onWritingTools() } }
    private val voice = IconButton(context, Icons.MIC, "Voice typing").apply { setOnClickListener { listener.onVoice() } }
    private val incognito = IconButton(context, Icons.SHIELD, "Not learning here").apply { iconSizeDp = 18f; isClickable = false }
    private val incognitoLabel = TextView(context).apply {
        text = "Not learning here"
        textSize = 14f
        typeface = Type.regular
        gravity = Gravity.CENTER_VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val center = FrameLayout(context)
    private val slots = List(3) { SuggestionSlot(context) }
    private val slotRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val dividers = List(2) { Divider(context) }
    private val chipScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; isFillViewport = false }
    private val chipRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    var inlineViews: List<View> = emptyList()
        private set
    // Autofill pills are live system surfaces, so they get their own row that is only rebuilt when
    // new pills arrive, never on each keystroke.
    private val inlineScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; isFillViewport = false }
    private val inlineRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private var lastState = StripState()
    private val slotData = arrayOfNulls<Suggestion>(3)
    private var undoSlot = -1

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val h = context.dp(48f).toInt()
        addView(tools, LayoutParams(h, h))
        addView(incognito, LayoutParams(context.dp(32f).toInt(), h))
        addView(center, LayoutParams(0, h, 1f))
        addView(voice, LayoutParams(h, h))

        slotRow.addView(slots[0], LayoutParams(0, h, 1f))
        slotRow.addView(dividers[0], LayoutParams(context.dp(1f).toInt(), context.dp(20f).toInt()))
        slotRow.addView(slots[1], LayoutParams(0, h, 1f))
        slotRow.addView(dividers[1], LayoutParams(context.dp(1f).toInt(), context.dp(20f).toInt()))
        slotRow.addView(slots[2], LayoutParams(0, h, 1f))
        slots.forEachIndexed { i, slot ->
            slot.setOnClickListener {
                if (i == undoSlot) listener.onUndoCorrection() else slotData[i]?.let(listener::onSuggestion)
            }
        }
        chipScroll.addView(chipRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h))
        center.addView(slotRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        center.addView(chipScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        center.addView(incognitoLabel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        inlineScroll.addView(inlineRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h))
        center.addView(inlineScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        inlineScroll.visibility = GONE
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.n.strip)
        tools.tint = c.n.textSecondary
        voice.tint = c.n.textSecondary
        incognito.tint = c.n.textSecondary
        incognitoLabel.setTextColor(c.n.textSecondary)
        slots.forEach { it.textColor = c.n.textPrimary; it.accent = c.accent.accent; it.invalidate() }
        dividers.forEach { it.color = c.n.divider }
        render(lastState, voice.visibility == VISIBLE, toolsActive)
    }

    private var toolsActive = false

    /** Shows the system-drawn autofill pills (e.g. from NordPass); an empty list removes them. */
    fun setInlineSuggestions(views: List<View>) {
        if (views == inlineViews) return
        inlineViews = views
        inlineRow.removeAllViews()
        views.forEach { v ->
            (v.parent as? ViewGroup)?.removeView(v)
            inlineRow.addView(v, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(6f).toInt() })
        }
        render(lastState, voice.visibility == VISIBLE, toolsActive)
    }

    fun render(state: StripState, voiceAvailable: Boolean, toolsOpen: Boolean) {
        lastState = state
        toolsActive = toolsOpen
        val c = colors ?: return
        tools.visibility = if (state.writingTools) VISIBLE else GONE
        tools.fill = if (toolsOpen) c.accent.container else 0
        tools.tint = if (toolsOpen) c.accent.onContainer else c.n.textSecondary
        voice.visibility = if (voiceAvailable) VISIBLE else GONE
        incognito.visibility = if (state.incognito) VISIBLE else GONE

        val code = state.code
        chipRow.removeAllViews()
        inlineScroll.visibility = GONE
        when {
            code != null -> {
                val chip = RichChip(
                    context, Icons.SMS, OtpDetector.grouped(code.code), "Copied code · hides in ${state.codeSecondsLeft} s",
                    c.n.key, c.n.textPrimary, c.n.textSecondary, c.n.special,
                ).apply {
                    contentDescription = "Insert code ${code.code.toCharArray().joinToString(" ")}. Hides in ${state.codeSecondsLeft} seconds."
                    setOnClickListener { listener.onCode() }
                }
                chip.titleView.textSize = 18f
                chip.titleView.typeface = Type.semibold
                chip.titleView.fontFeatureSettings = "tnum"
                chip.titleView.letterSpacing = 0.08f
                addChip(chip)
                showChips()
            }
            inlineViews.isNotEmpty() -> {
                slotRow.visibility = GONE
                incognitoLabel.visibility = GONE
                chipScroll.visibility = GONE
                inlineScroll.visibility = VISIBLE
                return
            }
            state.memory.isNotEmpty() -> {
                state.memory.forEach { item ->
                    val chip = RichChip(context, Icons.LOCK, item.preview(), "From private memory", c.accent.container, c.accent.onContainer, c.accent.onContainer, withAlpha(c.accent.onContainer, 0.12f)).apply {
                        contentDescription = "Paste ${item.type.label.lowercase()} from private memory"
                        setOnClickListener { listener.onMemory(item) }
                    }
                    addChip(chip)
                }
                showChips()
            }
            state.clip != null -> {
                val clip = state.clip!!
                val chip = RichChip(context, Icons.COPY, clip.preview(), "Copied · tap to paste", c.n.key, c.n.textPrimary, c.n.textSecondary, c.n.special).apply {
                    contentDescription = if (clip.sensitive) "Paste copied text, hidden" else "Paste copied text: ${clip.preview(60)}"
                    setOnClickListener { listener.onClip() }
                }
                addChip(chip)
                val close = IconButton(context, Icons.CLOSE, "Don’t offer copied text").apply {
                    tint = c.n.textSecondary
                    iconSizeDp = 18f
                    setOnClickListener { listener.onDismissClip() }
                }
                chipRow.addView(close, LayoutParams(context.dp(40f).toInt(), context.dp(40f).toInt()))
                showChips()
            }
            state.suggestions.isEmpty() && state.undoWord == null -> {
                chipScroll.visibility = GONE
                slotRow.visibility = GONE
                incognitoLabel.visibility = if (state.incognito) VISIBLE else GONE
            }
            else -> {
                chipScroll.visibility = GONE
                incognitoLabel.visibility = GONE
                slotRow.visibility = VISIBLE
                fillSlots(state)
            }
        }
    }

    private fun addChip(chip: View) {
        chipRow.addView(chip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(40f).toInt()).apply { marginEnd = context.dp(6f).toInt() })
    }

    private fun showChips() {
        slotRow.visibility = GONE
        incognitoLabel.visibility = GONE
        chipScroll.visibility = VISIBLE
    }

    private fun fillSlots(state: StripState) {
        val s = state.suggestions
        // Centre: the most likely word (index 0). Left: the word as typed or the undo word.
        val left: Suggestion?
        val right: Suggestion?
        if (state.undoWord != null) {
            left = Suggestion(state.undoWord!!, SuggestionKind.TYPED)
            right = s.getOrNull(1)
            undoSlot = 0
        } else {
            left = s.getOrNull(1)
            right = s.getOrNull(2)
            undoSlot = -1
        }
        val centre = s.getOrNull(0)
        slotData[0] = left
        slotData[1] = centre
        slotData[2] = right
        slots[0].text = left?.text ?: ""
        slots[1].text = centre?.text ?: ""
        slots[2].text = right?.text ?: ""
        slots[0].underline = state.undoWord != null || (state.autoCorrection != null && left?.kind == SuggestionKind.TYPED)
        slots[0].contentDescription = if (state.undoWord != null) "Undo correction, keep ${state.undoWord}" else left?.text
        slots[1].primary = centre != null
        slots[1].underline = false
        slots[2].primary = false
        slots[0].primary = false
        slots.forEach { it.isEnabled = it.text.isNotEmpty() }
        dividers.forEach { it.visibility = VISIBLE }
    }

    private class Divider(context: Context) : View(context) {
        var color = 0
            set(value) { field = value; invalidate() }
        private val p = Paint()
        override fun onDraw(canvas: Canvas) {
            p.color = color
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        }
    }
}
