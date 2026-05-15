package com.aidflow.pro.asr

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * On-device speech recognition using the system [SpeechRecognizer].
 *
 * On API 33+ we use `createOnDeviceSpeechRecognizer()` which guarantees no audio
 * leaves the device. On API 31–32 we fall back to the default recognizer with
 * `EXTRA_PREFER_OFFLINE=true` — the user's device must have the language pack
 * installed for this to actually work offline.
 *
 * The flow emits Partial events for visual feedback while the user speaks, then
 * a Final event when speech ends, then completes.
 */
class SpeechEngine(private val context: Context) {

    sealed interface Event {
        data class Partial(val text: String) : Event
        data class Final(val text: String) : Event
        data class Error(val code: Int, val message: String) : Event
        data object ReadyForSpeech : Event
        data object EndOfSpeech : Event
    }

    /** True if the OS offers a guaranteed offline recognizer (Android 13+). */
    fun isOnDeviceRecognitionAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
                .getOrDefault(false)

    /** True if any speech recognition service is installed (online or offline). */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(languageTag: String): Flow<Event> = callbackFlow {
        if (!isAvailable()) {
            trySend(Event.Error(SpeechRecognizer.ERROR_CLIENT, "No recognition service"))
            close()
            return@callbackFlow
        }

        val recognizer: SpeechRecognizer = if (isOnDeviceRecognitionAvailable()) {
            createOnDevice(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(Event.ReadyForSpeech)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                trySend(Event.EndOfSpeech)
            }

            override fun onError(error: Int) {
                trySend(Event.Error(error, errorMessage(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                trySend(Event.Final(text))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) trySend(Event.Partial(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)

        awaitClose {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    @RequiresApi(33)
    private fun createOnDevice(context: Context): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not installed on device"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language model unavailable"
        else -> "Speech error ($code)"
    }
}
