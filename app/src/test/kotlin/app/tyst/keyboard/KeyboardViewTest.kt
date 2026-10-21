package app.tyst.keyboard

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.layout.Key
import app.tyst.core.layout.KeyGeometry
import app.tyst.core.layout.KeyType
import app.tyst.core.layout.Layouts
import app.tyst.keyboard.ime.KeyboardView
import app.tyst.keyboard.ime.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(AndroidJUnit4::class)
class KeyboardViewTest {
    private lateinit var view: KeyboardView
    private val events = ArrayList<String>()
    private lateinit var geometry: KeyGeometry

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = KeyboardView(activity)
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: Key) { events += "key:" + (key.text.ifEmpty { key.type.name }) }
            override fun onPopupText(text: String) { events += "popup:$text" }
            override fun onPopupAction(action: String) { events += "action" }
            override fun onBackspace(step: Int) { events += "bs:$step" }
            override fun onSpaceLongPress() { events += "picker" }
        }
        val root = FrameLayout(activity)
        root.addView(view, FrameLayout.LayoutParams(1080, FrameLayout.LayoutParams.WRAP_CONTENT))
        activity.setContentView(root)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, 1080, view.measuredHeight)
        geometry = KeyGeometry(
            Layouts.letters(false, false), 1080f, activity.dp(52f), activity.dp(6f), activity.dp(10f), activity.dp(4f), activity.dp(4f), activity.dp(4f),
        )
    }

    private fun center(text: String) = geometry.keys.first { it.key.text == text }.slot
    private fun centerOf(type: KeyType) = geometry.keys.first { it.key.type == type }.slot

    private fun motion(action: Int, x: Float, y: Float): MotionEvent {
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, x, y, 0)
    }

    private fun tap(x: Float, y: Float) {
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y))
    }

    @Test fun heightFollowsKeyHeight() {
        assertThat(view.measuredHeight).isEqualTo(geometry.height.toInt())
    }

    @Test fun tapsTypeTheKeyUnderTheFinger() {
        for (k in listOf("h", "e", "j")) center(k).let { tap(it.centerX, it.centerY) }
        centerOf(KeyType.SPACE).let { tap(it.centerX, it.centerY) }
        assertThat(events).containsExactly("key:h", "key:e", "key:j", "key: ").inOrder()
    }

    @Test fun longPressOnAOffersSwedishLetters() {
        val a = center("a")
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, a.centerX, a.centerY))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(KeyboardView.LONG_PRESS_MS + 50))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, a.centerX, a.centerY))
        assertThat(events).containsExactly("popup:å")
    }

    @Test fun backspaceRepeatsWhileHeld() {
        val b = centerOf(KeyType.BACKSPACE)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, b.centerX, b.centerY))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(KeyboardView.REPEAT_START_MS + 2 * KeyboardView.REPEAT_EVERY_MS + 10))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, b.centerX, b.centerY))
        assertThat(events.first()).isEqualTo("bs:0")
        assertThat(events.size).isAtLeast(3)
        assertThat(events.none { it.startsWith("key:") }).isTrue()
    }

    @Test fun spaceLongPressOpensPicker() {
        val s = centerOf(KeyType.SPACE)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, s.centerX, s.centerY))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(KeyboardView.LONG_PRESS_MS + 50))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, s.centerX, s.centerY))
        assertThat(events).containsExactly("picker")
    }

    @Test fun describesKeysForTalkBack() {
        val a = Layouts.letters(false, false).find { it.text == "a" }!!
        assertThat(view.describe(a)).isEqualTo("a. Hold for å, ä")
        assertThat(view.describe(Layouts.letters(false, false).find { it.type == KeyType.BACKSPACE }!!)).isEqualTo("Delete")
        assertThat(view.describe(Layouts.letters(false, false).find { it.text == "," }!!)).contains("emoji")
    }

    @Test fun nonInteractivePreviewIgnoresTouches() {
        view.interactive = false
        val h = center("h")
        tap(h.centerX, h.centerY)
        assertThat(events).isEmpty()
    }
}
