package com.aidflow.pro.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.export.ExportFormat
import com.aidflow.pro.intake.IdentifiedItem
import com.aidflow.pro.intake.IntakeMapper
import com.aidflow.pro.intake.JsonExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Identify items in photo" flow: Gemma vision lists every relief item visible
 * in a supply photo, then the worker reviews/edits and exports a tabular
 * inventory (XLSX / CSV / TXT). Items accumulate across multiple photos within
 * one session.
 */
class ItemsViewModel(private val app: AidFlowApp) : ViewModel() {

    data class UiState(
        val pendingImage: Uri? = null,
        val items: List<IdentifiedItem> = emptyList(),
        val isIdentifying: Boolean = false,
        val isExporting: Boolean = false,
        val error: String? = null,
        val lastExportUri: Uri? = null,
        val lastExportMime: String? = null,
        val rawLlmReply: String = "",
    ) {
        val canExport get() = items.isNotEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun setImage(uri: Uri) {
        _state.value = _state.value.copy(pendingImage = uri, error = null)
    }

    fun identify(context: Context) {
        val uri = _state.value.pendingImage ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isIdentifying = true, error = null)
            try {
                val tmp = copyUriToCache(context, uri)
                val raw = try {
                    app.gemma.identifyItemsInImage(tmp)
                } finally {
                    tmp.delete()
                }
                val parsed = JsonExtractor.firstArray(raw)
                    ?: error("Gemma's reply didn't contain a parseable JSON array.")
                val added = IntakeMapper.itemsFromJson(parsed)
                _state.value = _state.value.copy(
                    items = _state.value.items + added,
                    isIdentifying = false,
                    pendingImage = null,
                    rawLlmReply = raw,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isIdentifying = false,
                    error = "Item identification failed: ${t.message}",
                )
            }
        }
    }

    fun remove(id: String) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
    }

    fun update(id: String, transform: (IdentifiedItem) -> IdentifiedItem) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == id) transform(it) else it },
        )
    }

    fun export(format: ExportFormat) {
        val cur = _state.value
        if (!cur.canExport) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, error = null)
            try {
                val uri = app.intakeExporter.exportItems(cur.items, format)
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
            val dir = File(context.cacheDir, "intake-photos").apply { mkdirs() }
            val file = File(dir, "items-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not read selected image")
            file
        }
}
