package com.aidflow.pro.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.asr.SpeechEngine
import com.aidflow.pro.export.ExportFormat
import com.aidflow.pro.intake.DisplacementStatus
import com.aidflow.pro.intake.FamilyRecord
import com.aidflow.pro.intake.IncomeLevel
import com.aidflow.pro.intake.IntakeMapper
import com.aidflow.pro.intake.JsonExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drives the Family Intake flow. Input is either a free-form description
 * (typed or spoken) or a photograph of a paper registration form. Gemma 4
 * extracts a structured [FamilyRecord] which the user can review/edit before
 * exporting an XLSX/CSV that imports straight into the AidFlow Pro web app.
 *
 * Multiple records can be captured in a single session and exported together.
 */
class FamilyIntakeViewModel(private val app: AidFlowApp) : ViewModel() {

    enum class InputMode { Speak, Type, Photo }

    data class UiState(
        val inputMode: InputMode = InputMode.Speak,
        val description: String = "",
        val partialAsr: String = "",
        val isListening: Boolean = false,
        val pendingImage: Uri? = null,
        val draft: FamilyRecord = FamilyRecord(),
        val captured: List<FamilyRecord> = emptyList(),
        val isExtracting: Boolean = false,
        val isExporting: Boolean = false,
        val lastExportUri: Uri? = null,
        val lastExportMime: String? = null,
        val error: String? = null,
        val rawLlmReply: String = "",
    ) {
        val canSave get() = draft.headName.isNotBlank()
        val canExport get() = captured.isNotEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val speech by lazy { SpeechEngine(app) }
    private var listenJob: Job? = null

    fun setInputMode(mode: InputMode) {
        _state.value = _state.value.copy(inputMode = mode, partialAsr = "", pendingImage = null)
    }

    fun setDescription(text: String) {
        _state.value = _state.value.copy(description = text)
    }

    fun setImage(uri: Uri) {
        _state.value = _state.value.copy(pendingImage = uri, inputMode = InputMode.Photo)
    }

    fun toggleListening(languageTag: String = "en-US") {
        if (_state.value.isListening) stopListening() else startListening(languageTag)
    }

    private fun startListening(languageTag: String) {
        if (!speech.isAvailable()) {
            _state.value = _state.value.copy(error = "Speech recognition isn't available on this device.")
            return
        }
        _state.value = _state.value.copy(isListening = true, partialAsr = "", error = null)
        listenJob = speech.listen(languageTag)
            .onEach { event ->
                when (event) {
                    is SpeechEngine.Event.Partial ->
                        _state.value = _state.value.copy(partialAsr = event.text)
                    is SpeechEngine.Event.Final ->
                        _state.value = _state.value.copy(
                            description = appendSentence(_state.value.description, event.text),
                            partialAsr = "",
                            isListening = false,
                        )
                    is SpeechEngine.Event.Error ->
                        _state.value = _state.value.copy(
                            isListening = false,
                            partialAsr = "",
                            error = event.message,
                        )
                    SpeechEngine.Event.EndOfSpeech ->
                        _state.value = _state.value.copy(isListening = false)
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

    private fun appendSentence(existing: String, addition: String): String {
        if (existing.isBlank()) return addition.trim()
        val sep = if (existing.endsWith('.') || existing.endsWith('!') || existing.endsWith('?')) " " else ". "
        return existing.trimEnd() + sep + addition.trim()
    }

    fun extract(context: Context) {
        val cur = _state.value
        viewModelScope.launch {
            _state.value = _state.value.copy(isExtracting = true, error = null)
            try {
                val rawJson = when (cur.inputMode) {
                    InputMode.Speak, InputMode.Type -> {
                        require(cur.description.isNotBlank()) { "Describe the family first." }
                        app.gemma.extractFamilyFromText(cur.description)
                    }
                    InputMode.Photo -> {
                        val uri = cur.pendingImage ?: error("Pick a photo first.")
                        val tmp = copyUriToCache(context, uri)
                        try {
                            app.gemma.extractFamilyFromImage(tmp)
                        } finally {
                            tmp.delete()
                        }
                    }
                }
                val parsed = JsonExtractor.firstObject(rawJson)
                    ?: error("Gemma's reply didn't contain a parseable JSON object.")
                val record = IntakeMapper.familyFromJson(parsed)
                _state.value = _state.value.copy(
                    draft = record,
                    isExtracting = false,
                    rawLlmReply = rawJson,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isExtracting = false,
                    error = "Extraction failed: ${t.message}",
                )
            }
        }
    }

    fun updateDraft(transform: (FamilyRecord) -> FamilyRecord) {
        _state.value = _state.value.copy(draft = transform(_state.value.draft))
    }

    fun saveDraft() {
        val cur = _state.value
        if (!cur.canSave) return
        _state.value = _state.value.copy(
            captured = cur.captured + cur.draft,
            draft = FamilyRecord(),
            description = "",
            pendingImage = null,
            rawLlmReply = "",
        )
    }

    fun removeCaptured(id: String) {
        _state.value = _state.value.copy(captured = _state.value.captured.filterNot { it.id == id })
    }

    fun export(format: ExportFormat) {
        val cur = _state.value
        if (!cur.canExport) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, error = null)
            try {
                val uri = app.intakeExporter.exportFamilies(cur.captured, format)
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

    fun setDisplacement(status: DisplacementStatus) =
        updateDraft { it.copy(displacementStatus = status) }

    fun setIncome(level: IncomeLevel) = updateDraft { it.copy(incomeLevel = level) }

    private suspend fun copyUriToCache(context: Context, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "intake-photos").apply { mkdirs() }
            val file = File(dir, "form-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not read selected image")
            file
        }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
    }
}
