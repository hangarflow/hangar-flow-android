package com.hangarflow.app.data.cloud

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Roger's ears and mouth on Android.
 *
 * `SpeechRecognizer` and `TextToSpeech` are on-device and cost nothing per
 * use, which is the whole reason Roger is viable at all: a mechanic says
 * "next" forty times in a job, and forty API calls would make the feature cost
 * more than it saves. What is said is classified locally by `HFRoger.intent`;
 * only a genuine question ever leaves the device.
 *
 * Push-to-talk, deliberately — not a wake word. A hangar is loud, a wake word
 * in that noise fires on compressor cycles and tool drops, and the first thing
 * a mechanic would do is turn it off. Holding a button also makes it obvious
 * when the microphone is live, which matters in a room with other people's
 * conversations in it.
 */
class HFRogerVoice(private val appContext: Context) {

    var listening by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    /** The last thing the recogniser thought it heard, for the UI to echo. */
    var heard by mutableStateOf<String?>(null)
        private set
    var unavailable by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingUtterance: String? = null

    /** Called with the FINAL transcript once the recogniser settles. */
    var onHeard: ((String) -> Unit)? = null

    fun prepare() {
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    // A walkthrough is read at working pace, not newsreader
                    // pace: the mechanic is doing the thing while it talks.
                    tts?.setSpeechRate(0.95f)
                    pendingUtterance?.let { say(it); pendingUtterance = null }
                } else {
                    unavailable = "This device has no speech output set up."
                }
            }
        }
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                unavailable = "This device has no speech recognition available."
                return
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listening = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { listening = false }
                    override fun onError(error: Int) {
                        listening = false
                        // Silence is not an error worth shouting about — in a
                        // hangar it is most of what the microphone gets.
                        if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                            error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            unavailable = "Didn't catch that."
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        val said = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (said.isNotEmpty()) {
                            heard = said
                            onHeard?.invoke(said)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        prepare()
        if (unavailable != null) return
        // Stop talking before listening, or the recogniser hears Roger.
        stopSpeaking()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // On-device where the platform can, so a hangar with no signal
            // still answers "next".
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { unavailable = "Couldn't start listening." }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
        listening = false
    }

    fun say(text: String) {
        if (text.isBlank()) return
        if (!ttsReady) { pendingUtterance = text; prepare(); return }
        speaking = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "roger")
        // TextToSpeech's progress listener is more plumbing than this needs;
        // the flag exists to drive a waveform, not to sequence anything.
        speaking = false
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        speaking = false
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        runCatching { tts?.shutdown() }
        recognizer = null
        tts = null
        ttsReady = false
    }
}
