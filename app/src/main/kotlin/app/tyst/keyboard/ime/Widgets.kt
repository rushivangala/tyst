package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.tyst.core.icons.Icon

/** A 48dp touch target that draws one icon, optionally on a filled circle or tile. */
@SuppressLint("ViewConstructor")
class IconButton(context: Context, icon: Icon, description: String) : View(context) {
    var icon: Icon = icon
        set(value) { field = value; invalidate() }
    var tint: Int = 0xFF000000.toInt()
        set(value) { field = value; invalidate() }
    var fill: Int = 0
        set(value) { field = value; invalidate() }
    var iconSizeDp = 22f
    var fillRadiusDp = 18f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = description
        isClickable = true
        isFocusable = true
        minimumWidth = context.dp(48f).toInt()
        minimumHeight = context.dp(48f).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = context.dp(48f).toInt()
        setMeasuredDimension(resolveSize(s, widthMeasureSpec), resolveSize(s, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (fill != 0) {
            paint.color = fill
            canvas.drawCircle(cx, cy, context.dp(fillRadiusDp), paint)
        }
        val alpha = if (isEnabled) 1f else 0.38f
        IconPainter.draw(canvas, icon, cx, cy, context.dp(iconSizeDp), withAlpha(tint, alpha * (tint ushr 24) / 255f))
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (pressed) 0.6f else 1f
    }
}

/** One suggestion slot: centred text, bold when primary, accent underline when kept as typed. */
@SuppressLint("ViewConstructor")
class SuggestionSlot(context: Context) : View(context) {
    var text: String = ""
        set(value) { field = value; contentDescription = value; invalidate() }
    var primary = false
        set(value) { field = value; invalidate() }
    var underline = false
        set(value) { field = value; invalidate() }
    var textColor = 0xFF000000.toInt()
    var accent = 0xFF0D5C58.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = context.sp(16f) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return
        paint.typeface = if (primary) Type.semibold else Type.regular
        paint.color = textColor
        val avail = width - context.dp(12f)
        val shown = TextUtils.ellipsize(text, android.text.TextPaint(paint), avail, TextUtils.TruncateAt.END).toString()
        val cy = height / 2f
        val baseline = cy - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(shown, width / 2f, baseline, paint)
        if (underline) {
            val w = paint.measureText(shown)
            line.color = accent
            val y = baseline + context.dp(5f)
            canvas.drawRect(width / 2f - w / 2, y, width / 2f + w / 2, y + context.dp(2f), line)
        }
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (pressed) 0.55f else 1f
    }
}

/** A rich chip (memory, code): icon tile, a title and a supporting line. 16dp corners. */
@SuppressLint("ViewConstructor")
class RichChip(context: Context, icon: Icon, title: String, subtitle: String, bg: Int, fg: Int, fgSecondary: Int, tileBg: Int) : LinearLayout(context) {
    val titleView: TextView
    val subtitleView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { cornerRadius = context.dp(16f); setColor(bg) }
        val padH = context.dp(8f).toInt()
        setPadding(padH, 0, context.dp(14f).toInt(), 0)
        isClickable = true
        isFocusable = true
        val tile = IconButton(context, icon, "").apply {
            tint = fg
            fill = 0
            iconSizeDp = 18f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply { cornerRadius = context.dp(12f); setColor(tileBg) }
            isClickable = false
        }
        val tileSize = context.dp(32f).toInt()
        addView(tile, LayoutParams(tileSize, tileSize).apply { marginEnd = context.dp(10f).toInt() })
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        titleView = TextView(context).apply {
            text = title
            setTextColor(fg)
            textSize = 14f
            typeface = Type.medium
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        subtitleView = TextView(context).apply {
            text = subtitle
            setTextColor(fgSecondary)
            textSize = 12f
            typeface = Type.regular
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(titleView)
        texts.addView(subtitleView)
        addView(texts, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (pressed) 0.7f else 1f
    }
}

/** A pill-shaped action chip: filled with the accent container when [selected], outlined otherwise. */
fun Context.actionChip(label: String, selected: Boolean, colors: app.tyst.core.theme.KeyboardColors, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Type.medium
        gravity = Gravity.CENTER
        minHeight = dp(36f).toInt()
        val padH = dp(16f).toInt()
        setPadding(padH, 0, padH, 0)
        isSelected = selected
        setTextColor(if (selected) colors.accent.onContainer else colors.n.textPrimary)
        background = GradientDrawable().apply {
            cornerRadius = dp(18f)
            if (selected) setColor(colors.accent.container) else {
                setColor(0)
                setStroke(dp(1f).toInt(), colors.n.outline)
            }
        }
        setOnClickListener { onClick() }
    }

/** A filled or outlined button for panels. */
fun Context.panelButton(label: String, filled: Boolean, colors: app.tyst.core.theme.KeyboardColors, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Type.medium
        gravity = Gravity.CENTER
        minHeight = dp(48f).toInt()
        val padH = dp(20f).toInt()
        setPadding(padH, 0, padH, 0)
        setTextColor(if (filled) colors.accent.onAccent else colors.accent.accent)
        background = GradientDrawable().apply {
            cornerRadius = dp(24f)
            if (filled) setColor(colors.accent.accent) else {
                setColor(0)
                setStroke(dp(1f).toInt(), colors.n.outline)
            }
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

/** The "On device" badge from the design system. */
fun Context.onDeviceBadge(colors: app.tyst.core.theme.KeyboardColors): TextView =
    TextView(this).apply {
        text = "On device"
        textSize = 12f
        typeface = Type.medium
        setTextColor(colors.n.success)
        val padH = dp(10f).toInt()
        setPadding(padH, dp(3f).toInt(), padH, dp(3f).toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(12f)
            setColor(0)
            setStroke(dp(1f).toInt(), colors.n.success)
        }
    }
