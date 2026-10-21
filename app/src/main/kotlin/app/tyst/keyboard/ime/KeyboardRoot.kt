package app.tyst.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.tyst.core.icons.Icons
import app.tyst.core.settings.OneHanded
import app.tyst.core.theme.KeyboardColors

/** Strip on top, then the keys or a panel. In one-handed mode the keys narrow to 82% beside a rail. */
@SuppressLint("ViewConstructor")
class KeyboardRoot(
    context: Context,
    val strip: StripView,
    val keyboard: KeyboardView,
    private val onSwapSide: () -> Unit,
    private val onExitOneHanded: () -> Unit,
) : LinearLayout(context) {
    private val content = FrameLayout(context)
    private val keysRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.BOTTOM }
    private val rail = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
    private val swap = IconButton(context, Icons.CHEVRON_LEFT, "Move keyboard to the other side").apply { setOnClickListener { onSwapSide() } }
    private val exit = IconButton(context, Icons.OPEN_IN_FULL, "Leave one-handed mode").apply { setOnClickListener { onExitOneHanded() } }
    private var panel: View? = null
    private var mode = OneHanded.OFF
    private var bottomInset = 0

    init {
        orientation = VERTICAL
        addView(strip, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(48f).toInt()))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rail.addView(swap)
        rail.addView(exit)
        content.addView(keysRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layoutKeys()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        if (nav != bottomInset) {
            bottomInset = nav
            setPadding(0, 0, 0, nav)
        }
        return insets
    }

    fun applyColors(c: KeyboardColors) {
        setBackgroundColor(c.n.plate)
        strip.applyColors(c)
        keyboard.colors = c
        swap.tint = c.n.textSecondary
        exit.tint = c.n.textSecondary
    }

    fun setOneHanded(m: OneHanded) {
        if (m == mode) return
        mode = m
        layoutKeys()
    }

    private fun layoutKeys() {
        keysRow.removeAllViews()
        if (mode == OneHanded.OFF) {
            keysRow.addView(keyboard, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return
        }
        swap.icon = if (mode == OneHanded.RIGHT) Icons.CHEVRON_LEFT else Icons.CHEVRON_RIGHT
        val keysLp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 82f)
        val railWeighted = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 18f)
        if (mode == OneHanded.RIGHT) {
            keysRow.addView(rail, railWeighted)
            keysRow.addView(keyboard, keysLp)
        } else {
            keysRow.addView(keyboard, keysLp)
            keysRow.addView(rail, railWeighted)
        }
    }

    val panelShown: View? get() = panel

    /** Replaces the keys with [view] at the same height, so the app below doesn't jump. */
    fun showPanel(view: View) {
        panel?.let { content.removeView(it) }
        panel = view
        val h = keyboard.height.takeIf { it > 0 } ?: keyboard.heightFor(width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels)
        keysRow.visibility = INVISIBLE
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
    }

    fun showKeys() {
        panel?.let { content.removeView(it) }
        panel = null
        keysRow.visibility = VISIBLE
    }
}
