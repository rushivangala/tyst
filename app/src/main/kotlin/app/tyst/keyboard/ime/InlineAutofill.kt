package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Size
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.inline.InlineContentView
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import app.tyst.core.theme.KeyboardColors

/**
 * Android's inline autofill. The autofill service (NordPass or any other) supplies the pills and the
 * system draws them; Tyst only places them in the strip and never sees what they contain.
 *
 * The request mirrors what password managers are known to accept: an explicit v1 chip style,
 * three presentation specs of a fixed height, and up to six suggestions.
 */
@SuppressLint("RestrictedApi")
object InlineAutofill {
    private const val SPECS = 3
    private const val MAX_SUGGESTIONS = 6

    fun request(context: Context, colors: KeyboardColors): InlineSuggestionsRequest {
        val chipBg = androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background
        val chip = ViewStyle.Builder()
            .setBackground(Icon.createWithResource(context, chipBg).setTint(colors.n.key))
            .build()
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackground(Icon.createWithResource(context, chipBg).setTint(colors.n.key))
                    .setPadding(0, 0, 0, 0)
                    .build(),
            )
            .setChipStyle(chip)
            .setStartIconStyle(ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
            .setTitleStyle(TextViewStyle.Builder().setTextColor(colors.n.textPrimary).setTextSize(14f).build())
            .setSubtitleStyle(TextViewStyle.Builder().setTextColor(colors.n.textSecondary).setTextSize(12f).build())
            .setEndIconStyle(ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
            .build()
        val styles = UiVersions.newStylesBuilder().addStyle(style).build()
        val height = context.dp(40f).toInt()
        val min = Size(context.dp(40f).toInt(), height)
        val max = Size(context.dp(320f).toInt(), height)
        val specs = List(SPECS) { InlinePresentationSpec.Builder(min, max).setStyle(styles).build() }
        return InlineSuggestionsRequest.Builder(specs).setMaxSuggestionCount(MAX_SUGGESTIONS).build()
    }

    /**
     * Inflates every pill and hands the list to [update] each time one is ready, in the order the
     * service sent them, so one pill that fails to draw can't hide the others.
     */
    fun inflate(context: Context, response: InlineSuggestionsResponse, update: (List<View>) -> Unit) {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            update(emptyList())
            return
        }
        val views = arrayOfNulls<View>(suggestions.size)
        val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        suggestions.forEachIndexed { i, s ->
            try {
                s.inflate(context, size, context.mainExecutor) { view ->
                    if (view == null) {
                        AutofillLog.add("Pill ${i + 1} could not be drawn")
                    } else {
                        views[i] = view
                        update(views.filterNotNull())
                    }
                }
            } catch (e: RuntimeException) {
                AutofillLog.add("Pill ${i + 1} failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}

/**
 * Keeps the system-drawn pill surfaces inside the strip: they belong to another process and would
 * otherwise draw over the rest of the keyboard while scrolling.
 */
class InlineClipFrame(context: Context) : FrameLayout(context) {
    private val parentBounds = Rect()
    private val contentBounds = Rect()
    private val onDraw = ViewTreeObserver.OnDrawListener { clip(this) }

    init {
        addView(SurfaceView(context).apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
        }, LayoutParams(0, 0))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnDrawListener(onDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnDrawListener(onDraw)
        super.onDetachedFromWindow()
    }

    private fun clip(root: View) {
        if (root === this) {
            parentBounds.set(0, 0, width, height)
        }
        when (root) {
            is InlineContentView -> {
                contentBounds.set(parentBounds)
                offsetRectIntoDescendantCoords(root, contentBounds)
                root.clipBounds = contentBounds
            }
            is ViewGroup -> for (i in 0 until root.childCount) clip(root.getChildAt(i))
        }
    }
}

/** The last few autofill events, in memory only, shown in Passwords & codes to diagnose problems. */
object AutofillLog {
    private val events = ArrayDeque<String>()

    /** Pills currently in the strip (for the end-to-end test). */
    @Volatile var shown = 0
    private val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)

    @Synchronized
    fun add(event: String) {
        events.addFirst("${time.format(java.util.Date())}  $event")
        while (events.size > 20) events.removeLast()
    }

    @Synchronized
    fun all(): List<String> = events.toList()
}
