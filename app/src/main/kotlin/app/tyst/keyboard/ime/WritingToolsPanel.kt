package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.tyst.core.icons.Icons
import app.tyst.core.theme.KeyboardColors
import app.tyst.core.write.Fix
import app.tyst.core.write.RewriteError
import app.tyst.core.write.RewriteException
import app.tyst.core.write.RewriteResult
import app.tyst.core.write.Tone

/**
 * Rewrite and Proofread, shown in place of the keys. Everything runs on the phone with Tyst's
 * rule-based writing engine, so results are instant and nothing is invented.
 */
@SuppressLint("ViewConstructor")
class WritingToolsPanel(context: Context, private val host: Host) : LinearLayout(context) {
    interface Host {
        val colors: KeyboardColors
        val appPackage: String
        val swedishWords: Boolean
        fun sourceText(): String
        fun replace(text: String)
        fun copy(text: String)
        fun rewrite(text: String, tone: Tone): Result<RewriteResult>
        fun proofread(text: String): List<Fix>
        fun applyFixes(text: String, fixes: List<Fix>): String
        fun close()
    }

    private enum class Tool { REWRITE, PROOFREAD }

    private var tool = Tool.REWRITE
    private var tone = Tone.MY_STYLE
    private var source = ""
    private val body = FrameLayout(context)
    private val toolRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val handler = Handler(Looper.getMainLooper())
    private var undoText: String? = null

    // Proofread session
    private var fixes: List<Fix> = emptyList()
    private var fixIndex = 0
    private val accepted = ArrayList<Fix>()

    init {
        orientation = VERTICAL
        val c = host.colors
        setBackgroundColor(c.n.plate)
        val pad = context.dp(12f).toInt()
        setPadding(pad, context.dp(4f).toInt(), pad, pad)

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "Writing tools"
            textSize = 16f
            typeface = Type.medium
            setTextColor(c.n.textPrimary)
        })
        header.addView(context.onDeviceBadge(c), LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(10f).toInt() })
        header.addView(android.view.View(context), LayoutParams(0, 1, 1f))
        header.addView(IconButton(context, Icons.KEYBOARD, "Back to keys").apply {
            tint = c.n.textSecondary
            setOnClickListener { host.close() }
        })
        addView(header)
        addView(toolRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(48f).toInt()))
        addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        start()
    }

    /** Reads the selection (or the whole field) again and shows the current tool. */
    fun start() {
        source = host.sourceText().trim()
        renderTools()
        when {
            source.isEmpty() -> showMessage("Nothing to work with yet", "Type a message or select some text, then pick a tool.")
            tool == Tool.REWRITE -> showRewrite()
            else -> startProofread()
        }
    }

    private fun renderTools() {
        val c = host.colors
        toolRow.removeAllViews()
        val gap = context.dp(8f).toInt()
        toolRow.addView(context.actionChip("Rewrite in my style", tool == Tool.REWRITE, c) { tool = Tool.REWRITE; start() },
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = gap })
        toolRow.addView(context.actionChip("Proofread", tool == Tool.PROOFREAD, c) { tool = Tool.PROOFREAD; start() })
    }

    private fun showMessage(title: String, text: String) {
        val c = host.colors
        body.removeAllViews()
        val col = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
        col.addView(TextView(context).apply { this.text = title; textSize = 16f; typeface = Type.medium; setTextColor(c.n.textPrimary); gravity = Gravity.CENTER })
        col.addView(TextView(context).apply { this.text = text; textSize = 14f; setTextColor(c.n.textSecondary); gravity = Gravity.CENTER; setPadding(0, context.dp(6f).toInt(), 0, 0) })
        body.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ---- rewrite ----

    private fun showRewrite() {
        val c = host.colors
        body.removeAllViews()
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        val tones = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        Tone.entries.forEach { t ->
            tones.addView(context.actionChip(t.label, t == tone, c) { tone = t; showRewrite() },
                LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(6f).toInt() })
        }
        col.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tones) },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(44f).toInt()))

        val result = host.rewrite(source, tone)
        val error = (result.exceptionOrNull() as? RewriteException)?.error
        if (error != null || result.isFailure) {
            val msg = if (error == RewriteError.TooLong) "That’s too long to rewrite at once. Select a shorter part." else "That didn’t work. Your text is unchanged. Try again."
            col.addView(TextView(context).apply { text = msg; textSize = 14f; setTextColor(c.n.textSecondary) })
            body.addView(col)
            return
        }
        val r = result.getOrThrow()
        val spanned = SpannableString(r.text)
        for (range in r.changed) {
            val end = minOf(range.last + 1, r.text.length)
            if (range.first < end) {
                spanned.setSpan(UnderlineSpan(), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanned.setSpan(BackgroundColorSpan(withAlpha(c.accent.container, 0.7f)), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val preview = TextView(context).apply {
            text = spanned
            textSize = 16f
            setTextColor(c.n.textPrimary)
            setLineSpacing(0f, 1.15f)
            val p = context.dp(12f).toInt()
            setPadding(p, p, p, p)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = context.dp(16f); setColor(c.n.key) }
        }
        col.addView(ScrollView(context).apply { addView(preview) }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(TextView(context).apply {
            text = (listOf("In my style".takeIf { tone == Tone.MY_STYLE }) + r.notes).filterNotNull().joinToString(" · ")
            textSize = 12f
            setTextColor(c.n.textSecondary)
            setPadding(0, context.dp(6f).toInt(), 0, context.dp(6f).toInt())
        })
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val undo = undoText
        if (undo != null) {
            actions.addView(TextView(context).apply { text = "Replaced"; textSize = 14f; setTextColor(c.n.textSecondary) }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(context.panelButton("Undo", filled = false, colors = c) {
                host.replace(undo)
                undoText = null
                start()
            })
        } else {
            actions.addView(context.panelButton("Replace", filled = true, colors = c) {
                undoText = source
                host.replace(r.text)
                source = r.text
                showRewrite()
                // The Undo offer stays for 5 seconds, as in the spec.
                handler.postDelayed({ if (undoText != null) { undoText = null; if (isAttachedToWindow) showRewrite() } }, 5_000)
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(8f).toInt() })
            actions.addView(context.panelButton("Copy", filled = false, colors = c) { host.copy(r.text) })
        }
        col.addView(actions)
        body.addView(col)
    }

    // ---- proofread ----

    private fun startProofread() {
        fixes = host.proofread(source)
        fixIndex = 0
        accepted.clear()
        if (fixes.isEmpty()) showMessage("Looks good", "No spelling or grammar slips found.") else showFix()
    }

    private fun showFix() {
        val c = host.colors
        body.removeAllViews()
        if (fixIndex >= fixes.size) {
            if (accepted.isNotEmpty()) {
                val fixed = host.applyFixes(source, accepted)
                host.replace(fixed)
                undoText = source
                source = fixed
            }
            showMessage("Done", if (accepted.isEmpty()) "No changes made." else "${accepted.size} ${if (accepted.size == 1) "fix" else "fixes"} applied.")
            return
        }
        val f = fixes[fixIndex]
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            text = "${f.kind.label} · ${fixIndex + 1} of ${fixes.size}"
            textSize = 12f
            typeface = Type.medium
            setTextColor(c.n.textSecondary)
        })
        val ctxStart = maxOf(0, f.start - 40)
        val ctxEnd = minOf(source.length, f.end + 40)
        val snippet = (if (ctxStart > 0) "…" else "") + source.substring(ctxStart, ctxEnd) + (if (ctxEnd < source.length) "…" else "")
        val offset = (if (ctxStart > 0) 1 else 0) - ctxStart
        val spanned = SpannableString(snippet)
        val hs = (f.start + offset).coerceIn(0, snippet.length)
        val he = (f.end + offset).coerceIn(hs, snippet.length)
        if (he > hs) {
            spanned.setSpan(BackgroundColorSpan(withAlpha(c.n.error, 0.18f)), hs, he, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spanned.setSpan(UnderlineSpan(), hs, he, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        col.addView(TextView(context).apply {
            text = spanned
            textSize = 15f
            setTextColor(c.n.textPrimary)
            setPadding(0, context.dp(6f).toInt(), 0, context.dp(6f).toInt())
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(TextView(context).apply {
            text = "“${f.original.ifBlank { "␣" }}” → “${f.replacement.ifEmpty { "remove" }}”"
            textSize = 16f
            typeface = Type.semibold
            setTextColor(c.n.textPrimary)
            setPadding(0, 0, 0, context.dp(8f).toInt())
        })
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        val lp = { LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(8f).toInt() } }
        actions.addView(context.panelButton("Accept", filled = true, colors = c) { accepted += f; fixIndex++; showFix() }, lp())
        actions.addView(context.panelButton("Skip", filled = false, colors = c) { fixIndex++; showFix() }, lp())
        actions.addView(context.panelButton("Accept all", filled = false, colors = c) { accepted += fixes.drop(fixIndex); fixIndex = fixes.size; showFix() })
        col.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(actions) })
        body.addView(col)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
