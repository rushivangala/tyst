package app.tyst.keyboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** A see-through activity: a keyboard can't ask for a permission itself, so this asks for the microphone. */
class MicPermissionActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            if (granted) "Microphone on. Tap the mic on the keyboard to start." else "Turn on the microphone in Android settings to use voice typing.",
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        request.launch(Manifest.permission.RECORD_AUDIO)
    }
}
