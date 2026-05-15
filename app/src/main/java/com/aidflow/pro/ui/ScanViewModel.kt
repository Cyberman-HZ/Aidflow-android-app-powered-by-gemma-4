package com.aidflow.pro.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.export.ExportDocument
import com.aidflow.pro.export.ExportFormat
import com.aidflow.pro.export.ExportScope
import com.aidflow.pro.export.Exporter
import com.aidflow.pro.translate.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State holder for the Scan & Translate Document flow.
 *
 * Stages: idle → recognized (raw OCR) → cleaned (Gemma cleanup OR Gemma vision) →
 * translated. The user can move forward step by step.
 *
 * OCR has two paths:
 *   • ML Kit Text Recognition v2 (fast, ~50ms, Latin script bundled)
 *   • Gemma 4 multimodal vision (slow, ~10–30s, handles handwriting / non-Latin
 *     / poor lighting much better)
 *
 * "Clean with Gemma" post-processes ML Kit output. "Re-read with Gemma vision"
 * replaces the OCR pass entirely.
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
        val isReadingWithGemma: Boolean = false,
        val isTranslating: Boolean = false,
        val isExporting: Boolean = false,
        val error: String? = null,
        val lastExportUri: Uri? = null,
        val lastExportMime: String? = null,
    ) {
        val hasRaw get() = rawText.isNotBlank()
        val hasCleaned get() = cleanedText.isNotBlank()
        val hasTranslated get() = translatedText.isNotBlank()
        val canExport get() = hasRaw || hasCleaned || hasTranslated
        val isBusy get() = isRecognizing || isCleaning || isReadingWithGemma ||
            isTranslating || isExporting
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

    fun recognize(context: Context) {
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

    /**
     * Sends the image directly to Gemma 4's multimodal vision pipeline as an
     * alternative OCR pass. Slower than ML Kit but recovers text that ML Kit
     * mangles: handwriting, low-contrast photos, rotated documents, non-Latin
     * scripts not in the bundled ML Kit model.
     */
    fun rereadWithGemmaVision(context: Context) {
        val uri = _state.value.imageUri ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isReadingWithGemma = true, error = null)
            try {
                val tempFile = copyUriToCache(context, uri)
                val text = app.gemma.describeImage(tempFile)
                _state.value = _state.value.copy(
                    cleanedText = text,
                    isReadingWithGemma = false,
                )
                tempFile.delete()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isReadingWithGemma = false,
                    error = "Gemma vision failed: ${t.message}",
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

    fun export(format: ExportFormat, scope: ExportScope) {
        val cur = _state.value
        if (!cur.canExport) return
        // Don't allow exporting a scope we don't have data for
        if (scope.includesTranslation && !cur.hasTranslated) {
            _state.value = _state.value.copy(error = "Translate first before exporting it.")
            return
        }
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
                val uri = exporter.export(doc, format, scope)
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

    private suspend fun copyUriToCache(context: Context, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "scan-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not read selected image")
            file
        }
}
