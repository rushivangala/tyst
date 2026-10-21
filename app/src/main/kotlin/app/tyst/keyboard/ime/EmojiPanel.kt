package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.tyst.core.emoji.Emoji
import app.tyst.core.icons.Icons
import app.tyst.core.theme.KeyboardColors

/** Offline emoji, with the user's own most used ones first. */
@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val colors: KeyboardColors,
    recent: List<String>,
    private val onEmoji: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    private val categories = buildList {
        if (recent.isNotEmpty()) add(Emoji.Category("recent", "Recent", "🕘", recent))
        addAll(Emoji.CATEGORIES)
    }
    private val grid = EmojiGrid(context)
    private val scroll = ScrollView(context)
    private val tabs = LinearLayout(context).apply { orientation = HORIZONTAL }
    private var current = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(colors.n.plate)
        scroll.addView(grid)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(TextView(context).apply {
            text = "ABC"
            textSize = 14f
            typeface = Type.medium
            gravity = Gravity.CENTER
            setTextColor(colors.n.textPrimary)
            background = GradientDrawable().apply { cornerRadius = context.dp(12f); setColor(this@EmojiPanel.colors.n.special) }
            contentDescription = "Back to letters"
            setOnClickListener { onClose() }
        }, LayoutParams(context.dp(64f).toInt(), context.dp(44f).toInt()).apply { marginStart = context.dp(4f).toInt() })
        bottom.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }, LayoutParams(0, context.dp(48f).toInt(), 1f))
        bottom.addView(IconButton(context, Icons.BACKSPACE, "Delete").apply {
            tint = colors.n.textPrimary
            setOnClickListener { onBackspace() }
        })
        addView(bottom)
        categories.forEachIndexed { i, cat ->
            tabs.addView(TextView(context).apply {
                text = cat.icon
                textSize = 20f
                gravity = Gravity.CENTER
                contentDescription = cat.label
                setOnClickListener { select(i) }
            }, LayoutParams(context.dp(44f).toInt(), context.dp(48f).toInt()))
        }
        select(0)
    }

    private fun select(i: Int) {
        current = i
        grid.emoji = categories[i].emoji
        scroll.scrollTo(0, 0)
        for (t in 0 until tabs.childCount) {
            tabs.getChildAt(t).background = if (t == i) GradientDrawable().apply { cornerRadius = context.dp(12f); setColor(this@EmojiPanel.colors.accent.container) } else null
        }
    }

    /** Draws emoji in a grid; one view instead of hundreds keeps the panel quick to open. */
    private inner class EmojiGrid(context: Context) : View(context) {
        var emoji: List<String> = emptyList()
            set(value) { field = value; requestLayout(); invalidate() }
        private val cell = context.dp(48f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = context.sp(26f) }
        private var columns = 8
        private var downIndex = -1

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            columns = maxOf(1, (w / cell).toInt())
            val rows = (emoji.size + columns - 1) / columns
            setMeasuredDimension(w, (rows * cell).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val cw = width / columns.toFloat()
            emoji.forEachIndexed { i, e ->
                val cx = (i % columns) * cw + cw / 2
                val cy = (i / columns) * cell + cell / 2
                canvas.drawText(e, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
            }
        }

        private fun indexAt(x: Float, y: Float): Int {
            val cw = width / columns.toFloat()
            val i = (y / cell).toInt() * columns + (x / cw).toInt()
            return if (i in emoji.indices) i else -1
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downIndex = indexAt(event.x, event.y)
                MotionEvent.ACTION_UP -> {
                    val i = indexAt(event.x, event.y)
                    if (i >= 0 && i == downIndex) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onEmoji(emoji[i])
                    }
                    downIndex = -1
                }
                MotionEvent.ACTION_CANCEL -> downIndex = -1
            }
            return true
        }
    }
}
