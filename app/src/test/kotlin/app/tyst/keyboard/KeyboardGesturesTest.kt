package app.tyst.keyboard

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.correct.TapOffset
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

/** Tap offsets on letter keys, the space-bar cursor slide and the backspace delete slide. */
@RunWith(AndroidJUnit4::class)
class KeyboardGesturesTest {
    private lateinit var view: KeyboardView
    private lateinit var activity: Activity
    private val events = ArrayList<String>()
    private var lastTap: TapOffset? = null
    private lateinit var geometry: KeyGeometry

    @Before fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = KeyboardView(activity)
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: Key) { events += "key:" + (key.text.ifEmpty { key.type.name }) }
            override fun onKeyTap(key: Key, tap: TapOffset?) { lastTap = tap; onKey(key) }
            override fun onPopupText(text: String) { events += "popup:$text" }
            override fun onPopupAction(action: String) { events += "action" }
            override fun onBackspace(step: Int) { events += "bs:$step" }
            override fun onSpaceLongPress() { events += "picker" }
            override fun onCursorSlide(steps: Int) { events += "cursor:$steps" }
            override fun onDeleteSlideStart() { events += "delete-start" }
            override fun onDeleteSlide(words: Int) { events += "delete:$words" }
            override fun onDeleteSlideEnd(commit: Boolean) { events += "delete-end:$commit" }
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

    private fun key(text: String) = geometry.keys.first { it.key.text == text }
    private fun key(type: KeyType) = geometry.keys.first { it.key.type == type }

    private fun motion(action: Int, x: Float, y: Float): MotionEvent {
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, x, y, 0)
    }

    private fun drag(fromX: Float, y: Float, toX: Float, steps: Int = 20) {
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, fromX, y))
        for (i in 1..steps) view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * i / steps, y))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, toX, y))
    }

    @Test fun letterTapReportsWhereTheFingerLanded() {
        val g = key("g")
        val x = g.visual.centerX + g.slot.width * 0.4f
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, g.visual.centerY))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, g.visual.centerY))
        assertThat(events).containsExactly("key:g")
        assertThat(lastTap!!.dx).isWithin(0.02f).of(0.4f)
        assertThat(lastTap!!.dy).isWithin(0.02f).of(0f)
    }

    @Test fun spaceSlideMovesTheCursorAndTypesNoSpace() {
        val space = key(KeyType.SPACE).visual
        drag(space.centerX, space.centerY, space.centerX - activity.dp(14f + 12f * 3 + 2f))
        assertThat(events.filter { it.startsWith("cursor:") }.sumOf { it.removePrefix("cursor:").toInt() }).isEqualTo(-3)
        assertThat(events).doesNotContain("key: ")
        assertThat(events).doesNotContain("picker")
    }

    @Test fun smallWobbleOnSpaceStillTypesASpace() {
        val space = key(KeyType.SPACE).visual
        drag(space.centerX, space.centerY, space.centerX + activity.dp(6f), steps = 3)
        assertThat(events).containsExactly("key: ")
    }

    @Test fun backspaceSlideSelectsWordsAndDeletesOnRelease() {
        val bs = key(KeyType.BACKSPACE).visual
        drag(bs.centerX, bs.centerY, bs.centerX - activity.dp(14f + 28f + 4f))
        assertThat(events.first()).isEqualTo("bs:0")
        assertThat(events).containsAtLeast("delete-start", "delete:1", "delete:2", "delete-end:true").inOrder()
    }
}
