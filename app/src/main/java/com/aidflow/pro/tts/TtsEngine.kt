package com.aidflow.pro.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Lazy wrapper over [TextToSpeech]. Synthesis is best-effort: if the user's device
 * doesn't have a voice installed for the requested language, we degrade gracefully
 * by returning [SpeakResult.LanguageUnavailable] rather than throwing.
 */
class TtsEngine(context: Context) {

    sealed interface SpeakResult {
        data object Ok : SpeakResult
        data object NotReady : SpeakResult
        data object LanguageUnavailable : SpeakResult
        data class Error(val message: String) : SpeakResult
    }

    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = (status == TextToSpeech.SUCCESS)
    }

    suspend fun speak(text: String, locale: Locale): SpeakResult {
        if (!ready) return SpeakResult.NotReady
        if (text.isBlank()) return SpeakResult.Ok

        val supportCode = tts.isLanguageAvailable(locale)
        if (supportCode == TextToSpeech.LANG_MISSING_DATA ||
            supportCode == TextToSpeech.LANG_NOT_SUPPORTED) {
            return SpeakResult.LanguageUnavailable
        }
        tts.language = locale

        return suspendCancellableCoroutine { cont ->
            val id = "aidflow-${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(SpeakResult.Ok)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(SpeakResult.Error("TTS failed"))
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(SpeakResult.Error("TTS error $errorCode"))
                }
            })
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(SpeakResult.Error("Unable to enqueue utterance"))
            }
            cont.invokeOnCancellation { runCatching { tts.stop() } }
        }
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}
