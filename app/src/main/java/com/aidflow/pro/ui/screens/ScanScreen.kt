package com.aidflow.pro.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aidflow.pro.R
import com.aidflow.pro.export.ExportFormat
import com.aidflow.pro.export.ExportScope
import com.aidflow.pro.ui.ScanViewModel
import com.aidflow.pro.ui.appViewModel
import com.aidflow.pro.ui.components.BusyOverlay
import com.aidflow.pro.ui.components.LanguagePicker
import com.aidflow.pro.ui.components.PhotoSourceRow
import com.aidflow.pro.ui.components.rememberDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val vm: ScanViewModel = appViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.setImage(uri)
            vm.recognize(context)
        }
    }
    val captureImage = rememberDocumentScanner(
        scannerMode = GmsDocumentScannerOptions.SCANNER_MODE_FULL,
        onCaptured = { uri ->
            vm.setImage(uri)
            vm.recognize(context)
        },
    )

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.lastExportUri) {
        val uri = state.lastExportUri ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = context.getString(R.string.export_saved),
            actionLabel = context.getString(R.string.export_share),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            val mime = state.lastExportMime ?: "*/*"
            val share = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            (context as? Activity)?.startActivity(Intent.createChooser(share, "Share via"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LanguagePicker(
                        label = stringResource(R.string.from_language),
                        selected = state.sourceLang,
                        onSelect = vm::setSourceLanguage,
                        modifier = Modifier.weight(1f),
                    )
                    LanguagePicker(
                        label = stringResource(R.string.to_language),
                        selected = state.targetLang,
                        onSelect = vm::setTargetLanguage,
                        modifier = Modifier.weight(1f),
                    )
                }

                PhotoSourceRow(
                    onGalleryPick = {
                        pickImage.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onCameraCapture = captureImage,
                    galleryLabel = stringResource(R.string.scan_pick_photo),
                    cameraLabel = stringResource(R.string.scan_take_photo),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.rawText.isBlank() && !state.isRecognizing) {
                    EmptyState()
                } else {
                    OutputCard(
                        title = stringResource(R.string.scan_tab_original),
                        text = state.rawText,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = vm::cleanWithGemma,
                            enabled = state.hasRaw && !state.isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_clean_gemma))
                        }
                        OutlinedButton(
                            onClick = { vm.rereadWithGemmaVision(context) },
                            enabled = state.imageUri != null && !state.isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_reread_gemma))
                        }
                    }
                    if (state.hasCleaned) {
                        OutputCard(
                            title = stringResource(R.string.scan_tab_cleaned),
                            text = state.cleanedText,
                        )
                    }
                    Button(
                        onClick = vm::translate,
                        enabled = state.hasRaw && !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_translate))
                    }
                    if (state.hasTranslated) {
                        OutputCard(
                            title = stringResource(R.string.scan_tab_translated),
                            text = state.translatedText,
                        )
                    }
                    if (state.canExport) {
                        Button(
                            onClick = { showExport = true },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_export))
                        }
                    }
                }
            }

            BusyOverlay(
                visible = state.isBusy,
                label = when {
                    state.isRecognizing -> "Recognizing text…"
                    state.isCleaning -> "Cleaning with Gemma 4…"
                    state.isReadingWithGemma -> "Reading image with Gemma 4 vision…"
                    state.isTranslating -> "Translating with Gemma 4…"
                    state.isExporting -> "Exporting…"
                    else -> ""
                },
            )

            if (showExport) {
                ExportSheet(
                    hasTranslation = state.hasTranslated,
                    onDismiss = { showExport = false },
                    onExport = { scope, fmt ->
                        showExport = false
                        vm.export(fmt, scope)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card {
        Text(
            stringResource(R.string.scan_empty),
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OutputCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    hasTranslation: Boolean,
    onDismiss: () -> Unit,
    onExport: (ExportScope, ExportFormat) -> Unit,
) {
    var scope by remember(hasTranslation) {
        mutableStateOf(if (hasTranslation) ExportScope.Both else ExportScope.Original)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleLarge)

            Text(
                stringResource(R.string.export_scope_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScopeChips(
                selected = scope,
                onSelect = { scope = it },
                translationEnabled = hasTranslation,
            )

            Text(
                stringResource(R.string.export_format_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormatRow(stringResource(R.string.export_txt)) { onExport(scope, ExportFormat.Txt) }
            FormatRow(stringResource(R.string.export_csv)) { onExport(scope, ExportFormat.Csv) }
            FormatRow(stringResource(R.string.export_pdf)) { onExport(scope, ExportFormat.Pdf) }
            FormatRow(stringResource(R.string.export_docx)) { onExport(scope, ExportFormat.Docx) }
        }
    }
}

@Composable
private fun ScopeChips(
    selected: ExportScope,
    onSelect: (ExportScope) -> Unit,
    translationEnabled: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == ExportScope.Original,
            onClick = { onSelect(ExportScope.Original) },
            label = { Text(stringResource(R.string.export_scope_original)) },
        )
        FilterChip(
            selected = selected == ExportScope.Translation,
            onClick = { onSelect(ExportScope.Translation) },
            enabled = translationEnabled,
            label = { Text(stringResource(R.string.export_scope_translation)) },
        )
        FilterChip(
            selected = selected == ExportScope.Both,
            onClick = { onSelect(ExportScope.Both) },
            enabled = translationEnabled,
            label = { Text(stringResource(R.string.export_scope_both)) },
        )
    }
}

@Composable
private fun FormatRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(label)
        }
    }
}
