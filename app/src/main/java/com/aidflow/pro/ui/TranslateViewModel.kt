package com.aidflow.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.asr.SpeechEngine
import com.aidflow.pro.translate.Language
import com.aidflow.pro.tts.TtsEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Real-time translate screen. Drives [SpeechEngine] for voice input and
 * [com.aidflow.pro.translate.TranslationRepository] for text/voice translation,
 * then plays the result through [TtsEngine].
 *
 * To keep on-device CPU latency tolerable, we only ask Gemma to translate on a
 * FINAL ASR result — partial results just update the live transcript display.
 */
class TranslateViewModel(private val app: AidFlowApp) : ViewModel() {

    data class UiState(
        val sourceLang: Language = Language.English,
        val targetLang: Language = Language.Arabic,
        val inputText: String = "",
        val translation: String = "",
        val isListening: Boolean = false,
        val partialAsr: String = "",
        val isTranslating: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val speech = SpeechEngine(app)
    private val tts = TtsEngine(app)
    private var listenJob: Job? = null

    fun isOnDeviceAsrAvailable(): Boolean = speech.isOnDeviceRecognitionAvailable()

    fun setSource(lang: Language) {
        _state.value = _state.value.copy(sourceLang = lang)
    }
    fun setTarget(lang: Language) {
        _state.value = _state.value.copy(targetLang = lang)
    }
    fun swapLanguages() {
        _state.value = _state.value.copy(
            sourceLang = _state.value.targetLang,
            targetLang = _state.value.sourceLang,
            inputText = _state.value.translation,
            translation = _state.value.inputText,
        )
    }

    fun setInput(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun toggleListening() {
        if (_state.value.isListening) stopListening() else startListening()
    }

    private fun startListening() {
        if (!speech.isAvailable()) {
            _state.value = _state.value.copy(error = "Speech recognition isn't available on this device.")
            return
        }
        _state.value = _state.value.copy(isListening = true, partialAsr = "", error = null)
        listenJob = speech.listen(_state.value.sourceLang.tag)
            .onEach { event ->
                when (event) {
                    is SpeechEngine.Event.Partial -> {
                        _state.value = _state.value.copy(partialAsr = event.text)
                    }
                    is SpeechEngine.Event.Final -> {
                        _state.value = _state.value.copy(
                            inputText = event.text,
                            partialAsr = "",
                            isListening = false,
                        )
                        translateAndSpeak(event.text)
                    }
                    is SpeechEngine.Event.Error -> {
                        _state.value = _state.value.copy(
                            isListening = false,
                            partialAsr = "",
                            error = event.message,
                        )
                    }
                    SpeechEngine.Event.EndOfSpeech -> {
                        _state.value = _state.value.copy(isListening = false)
                    }
                    SpeechEngine.Event.ReadyForSpeech -> Unit
                }
            }
            .launchIn(viewModelScope)
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _state.value = _state.value.copy(isListening = false, partialAsr = "")
    }

    fun translateInput() {
        translateAndSpeak(_state.value.inputText, speak = false)
    }

    private fun translateAndSpeak(text: String, speak: Boolean = true) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isTranslating = true, error = null)
            try {
                val cur = _state.value
                val translated = app.translation.translate(text, cur.sourceLang, cur.targetLang)
                _state.value = _state.value.copy(translation = translated, isTranslating = false)
                if (speak) speakTranslation()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isTranslating = false,
                    error = "Translation failed: ${t.message}",
                )
            }
        }
    }

    fun speakTranslation() {
        val text = _state.value.translation
        val locale = _state.value.targetLang.toLocale()
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val r = tts.speak(text, locale)) {
                is TtsEngine.SpeakResult.LanguageUnavailable ->
                    _state.value = _state.value.copy(
                        error = "Text-to-speech isn't installed for ${_state.value.targetLang.displayName}.",
                    )
                is TtsEngine.SpeakResult.Error ->
                    _state.value = _state.value.copy(error = r.message)
                else -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
        tts.shutdown()
    }
}
