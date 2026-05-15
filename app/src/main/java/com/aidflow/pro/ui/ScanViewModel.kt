package com.aidflow.pro.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.export.ExportDocument
import com.aidflow.pro.export.ExportFormat
import com.aidflow.pro.export.Exporter
import com.aidflow.pro.translate.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for the Scan & Translate Document flow.
 *
 * Stages: idle → recognized (raw OCR) → cleaned (Gemma cleanup) → translated.
 * The UI lets the user move forward step by step, both so they can see the
 * intermediate output (which is itself useful) and so a slow Gemma cleanup
 * doesn't block the simple "show me the text" outcome.
 */
class ScanViewModel(
    private val app: AidFlowApp,
    private val exporter: Exporter,
) : ViewModel() {

    data class UiState(
        val imageUri: Uri? = null,
        val sourceLang: Language = Language.Spanish,
        val targetLang: Language = Language.English,
        val rawText: String = "",
        val cleanedText: String = "",
        val translatedText: String = "",
        val isRecognizing: Boolean = false,
        val isCleaning: Boolean = false,
        val isTranslating: Boolean = false,
        val isExporting: Boolean = false,
        val error: String? = null,
        val lastExportUri: Uri? = null,
        val lastExportMime: String? = null,
    ) {
        val hasRaw get() = rawText.isNotBlank()
        val hasCleaned get() = cleanedText.isNotBlank()
        val hasTranslated get() = translatedText.isNotBlank()
        val readyToExport get() = hasRaw && hasTranslated
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun setImage(uri: Uri) {
        _state.value = _state.value.copy(
            imageUri = uri,
            rawText = "",
            cleanedText = "",
            translatedText = "",
            error = null,
        )
    }

    fun setSourceLanguage(lang: Language) {
        _state.value = _state.value.copy(sourceLang = lang)
    }

    fun setTargetLanguage(lang: Language) {
        _state.value = _state.value.copy(targetLang = lang)
    }

    fun recognize(context: android.content.Context) {
        val uri = _state.value.imageUri ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRecognizing = true, error = null)
            try {
                val result = app.ocr.recognize(context, uri)
                _state.value = _state.value.copy(
                    rawText = result.text.ifBlank { "" },
                    isRecognizing = false,
                    error = if (result.isEmpty) "No text was detected in this image." else null,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isRecognizing = false,
                    error = "OCR failed: ${t.message}",
                )
            }
        }
    }

    fun cleanWithGemma() {
        val raw = _state.value.rawText
        if (raw.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCleaning = true, error = null)
            try {
                val cleaned = app.translation.cleanOcr(raw)
                _state.value = _state.value.copy(cleanedText = cleaned, isCleaning = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isCleaning = false,
                    error = "Gemma cleanup failed: ${t.message}",
                )
            }
        }
    }

    fun translate() {
        val cur = _state.value
        val text = cur.cleanedText.ifBlank { cur.rawText }
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isTranslating = true, error = null)
            try {
                val translated = app.translation.translate(text, cur.sourceLang, cur.targetLang)
                _state.value = _state.value.copy(translatedText = translated, isTranslating = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isTranslating = false,
                    error = "Translation failed: ${t.message}",
                )
            }
        }
    }

    fun export(format: ExportFormat) {
        val cur = _state.value
        if (!cur.readyToExport) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, error = null)
            try {
                val doc = ExportDocument.fromOcrPipeline(
                    title = "AidFlow Scan",
                    sourceLang = cur.sourceLang.englishName,
                    targetLang = cur.targetLang.englishName,
                    originalLines = (cur.cleanedText.ifBlank { cur.rawText }).lines(),
                    translatedFullText = cur.translatedText,
                )
                val uri = exporter.export(doc, format)
                _state.value = _state.value.copy(
                    isExporting = false,
                    lastExportUri = uri,
                    lastExportMime = format.mime,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isExporting = false,
                    error = "Export failed: ${t.message}",
                )
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
