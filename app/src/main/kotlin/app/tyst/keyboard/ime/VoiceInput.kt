package app.tyst.keyboard.ime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Voice typing with Android's on-device speech recogniser only. If the phone has no on-device
 * recogniser, voice typing is unavailable; Tyst never falls back to a network recogniser.
 */
class VoiceInput(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onLevel(rms: Float)
        fun onError(message: String, permissionMissing: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null

    val isListening: Boolean get() = recognizer != null

    fun available(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start() {
        stop()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = listener.onLevel(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                release()
                listener.onError(message(error), error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release()
                listener.onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(listener::onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val locale = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.US
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun message(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn’t catch that. Tap the mic to try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone is off for Tyst"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The English speech pack isn’t on this phone yet. Add it in Android settings."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice typing is busy. Try again in a moment."
        else -> "That didn’t work. Try again."
    }
}
