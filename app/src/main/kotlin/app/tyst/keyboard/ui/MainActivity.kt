package app.tyst.keyboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.tyst.keyboard.graph

/** Onboarding and settings. Shares the keyboard's process, so changes apply at once. */
class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TystRoot(graph, resumeTick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        // Re-check "keyboard enabled", the autofill service and permissions after visiting Android settings.
        resumeTick.intValue++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog; when it closes we only regain focus.
        if (hasFocus) resumeTick.intValue++
    }

    override fun onStop() {
        graph.saveNow()
        super.onStop()
    }
}
