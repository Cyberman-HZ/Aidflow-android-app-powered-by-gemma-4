package com.aidflow.pro.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aidflow.pro.R
import com.aidflow.pro.ui.TranslateViewModel
import com.aidflow.pro.ui.appViewModel
import com.aidflow.pro.ui.components.LanguagePicker
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TranslateScreen(onBack: () -> Unit) {
    val vm: TranslateViewModel = appViewModel()
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_translate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Language row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LanguagePicker(
                    label = stringResource(R.string.from_language),
                    selected = state.sourceLang,
                    onSelect = vm::setSource,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::swapLanguages) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = stringResource(R.string.translate_swap_langs),
                    )
                }
                LanguagePicker(
                    label = stringResource(R.string.to_language),
                    selected = state.targetLang,
                    onSelect = vm::setTarget,
                    modifier = Modifier.weight(1f),
                )
            }

            // Source card
            Card(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxSize()) {
                    OutlinedTextField(
                        value = if (state.isListening && state.partialAsr.isNotBlank()) state.partialAsr else state.inputText,
                        onValueChange = vm::setInput,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text(stringResource(R.string.translate_hint)) },
                        readOnly = state.isListening,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (micPermission.status.isGranted) vm.toggleListening()
                                else micPermission.launchPermissionRequest()
                            },
                        ) {
                            Icon(
                                if (state.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.isListening) stringResource(R.string.translate_stop)
                                else stringResource(R.string.translate_speak),
                            )
                        }
                        Button(
                            onClick = vm::translateInput,
                            enabled = state.inputText.isNotBlank() && !state.isTranslating,
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scan_translate))
                        }
                        if (state.isTranslating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (!micPermission.status.isGranted && micPermission.status.shouldShowRationale) {
                        Text(
                            stringResource(R.string.perm_mic_rationale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Translation card
            Card(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxSize()) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            state.translation.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = vm::speakTranslation,
                            enabled = state.translation.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.translate_play))
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.translation))
                            },
                            enabled = state.translation.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.translate_copy))
                        }
                    }
                }
            }
        }
    }
}
