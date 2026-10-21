package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.tyst.core.icons.Icons
import app.tyst.core.theme.KeyboardColors

/** Listening state for on-device voice typing: live transcript and the "On device" badge. */
@SuppressLint("ViewConstructor")
class VoicePanel(context: Context, colors: KeyboardColors, onStop: () -> Unit) : LinearLayout(context) {
    private val status: TextView
    private val transcript: TextView
    private val mic: IconButton

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(colors.n.plate)
        val pad = context.dp(16f).toInt()
        setPadding(pad, pad, pad, pad)
        addView(context.onDeviceBadge(colors))
        status = TextView(context).apply {
            text = "Listening…"
            textSize = 16f
            typeface = Type.medium
            setTextColor(colors.n.textPrimary)
            gravity = Gravity.CENTER
            setPadding(0, context.dp(12f).toInt(), 0, 0)
        }
        addView(status)
        transcript = TextView(context).apply {
            textSize = 16f
            setTextColor(colors.n.textSecondary)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(0, context.dp(8f).toInt(), 0, context.dp(8f).toInt())
        }
        addView(transcript, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mic = IconButton(context, Icons.MIC, "Stop listening").apply {
            fill = colors.accent.accent
            tint = colors.accent.onAccent
            fillRadiusDp = 32f
            iconSizeDp = 28f
            setOnClickListener { onStop() }
        }
        addView(mic, LayoutParams(context.dp(80f).toInt(), context.dp(80f).toInt()))
    }

    fun setStatus(text: String) { status.text = text }
    fun setTranscript(text: String) { transcript.text = text }
    fun setLevel(rms: Float) {
        val s = 1f + (rms.coerceIn(0f, 10f) / 10f) * 0.15f
        mic.scaleX = s
        mic.scaleY = s
    }
}
