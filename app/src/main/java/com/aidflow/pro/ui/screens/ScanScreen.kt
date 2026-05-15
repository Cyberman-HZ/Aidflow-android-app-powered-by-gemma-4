package com.aidflow.pro.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Translate
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
import com.aidflow.pro.ui.ScanViewModel
import com.aidflow.pro.ui.appViewModel
import com.aidflow.pro.ui.components.BusyOverlay
import com.aidflow.pro.ui.components.LanguagePicker

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

                FilledTonalButton(
                    onClick = {
                        pickImage.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_pick_photo))
                }

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
                            enabled = state.hasRaw && !state.isCleaning,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_clean_gemma))
                        }
                        Button(
                            onClick = vm::translate,
                            enabled = state.hasRaw && !state.isTranslating,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_translate))
                        }
                    }
                    if (state.hasCleaned) {
                        OutputCard(
                            title = stringResource(R.string.scan_tab_cleaned),
                            text = state.cleanedText,
                        )
                    }
                    if (state.hasTranslated) {
                        OutputCard(
                            title = stringResource(R.string.scan_tab_translated),
                            text = state.translatedText,
                        )
                        Button(
                            onClick = { showExport = true },
                            enabled = state.readyToExport && !state.isExporting,
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
                visible = state.isRecognizing || state.isCleaning ||
                    state.isTranslating || state.isExporting,
                label = when {
                    state.isRecognizing -> "Recognizing text…"
                    state.isCleaning -> "Cleaning with Gemma 4…"
                    state.isTranslating -> "Translating with Gemma 4…"
                    state.isExporting -> "Exporting…"
                    else -> ""
                },
            )

            if (showExport) {
                ExportSheet(
                    onDismiss = { showExport = false },
                    onPick = { fmt ->
                        showExport = false
                        vm.export(fmt)
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
private fun ExportSheet(onDismiss: () -> Unit, onPick: (ExportFormat) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ExportRow(stringResource(R.string.export_txt)) { onPick(ExportFormat.Txt) }
            ExportRow(stringResource(R.string.export_csv)) { onPick(ExportFormat.Csv) }
            ExportRow(stringResource(R.string.export_pdf)) { onPick(ExportFormat.Pdf) }
            ExportRow(stringResource(R.string.export_docx)) { onPick(ExportFormat.Docx) }
        }
    }
}

@Composable
private fun ExportRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(label)
        }
    }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer(content = content)
}
