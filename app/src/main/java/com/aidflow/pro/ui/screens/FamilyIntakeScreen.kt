package com.aidflow.pro.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aidflow.pro.export.ExportFormat
import com.aidflow.pro.intake.DisplacementStatus
import com.aidflow.pro.intake.FamilyRecord
import com.aidflow.pro.intake.IncomeLevel
import com.aidflow.pro.ui.FamilyIntakeViewModel
import com.aidflow.pro.ui.appViewModel
import com.aidflow.pro.ui.components.BusyOverlay
import com.aidflow.pro.ui.components.PhotoSourceRow
import com.aidflow.pro.ui.components.rememberCameraCapture
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FamilyIntakeScreen(onBack: () -> Unit) {
    val vm: FamilyIntakeViewModel = appViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExport by remember { mutableStateOf(false) }
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.setImage(uri) }
    val captureImage = rememberCameraCapture { uri -> vm.setImage(uri) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.lastExportUri) {
        val uri = state.lastExportUri ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Saved to Downloads", actionLabel = "Share", duration = SnackbarDuration.Long)
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
                title = { Text("Family intake") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                Text(
                    "Describe one family or scan their paper registration form. Gemma 4 turns it into a structured record that imports straight into the AidFlow web app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                InputModeRow(
                    selected = state.inputMode,
                    onSelect = vm::setInputMode,
                )

                when (state.inputMode) {
                    FamilyIntakeViewModel.InputMode.Speak -> SpeakInput(
                        state = state,
                        micGranted = micPermission.status.isGranted,
                        onRequestMic = { micPermission.launchPermissionRequest() },
                        onToggleListening = { vm.toggleListening() },
                        onDescription = vm::setDescription,
                    )
                    FamilyIntakeViewModel.InputMode.Type -> TypeInput(
                        description = state.description,
                        onDescription = vm::setDescription,
                    )
                    FamilyIntakeViewModel.InputMode.Photo -> PhotoInput(
                        hasImage = state.pendingImage != null,
                        onPick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCapture = captureImage,
                    )
                }

                Button(
                    onClick = { vm.extract(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isExtracting && when (state.inputMode) {
                        FamilyIntakeViewModel.InputMode.Photo -> state.pendingImage != null
                        else -> state.description.isNotBlank()
                    },
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Extract with Gemma 4")
                }

                if (state.draft.headName.isNotBlank() ||
                    state.draft.memberCount > 0 ||
                    state.draft.medicalConditions.isNotBlank()) {
                    DraftCard(
                        draft = state.draft,
                        onUpdate = vm::updateDraft,
                        onSetDisplacement = vm::setDisplacement,
                        onSetIncome = vm::setIncome,
                        onSave = vm::saveDraft,
                    )
                }

                if (state.captured.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Captured this session (${state.captured.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.captured.forEach { record ->
                        CapturedRow(record = record, onRemove = { vm.removeCaptured(record.id) })
                    }
                    Button(
                        onClick = { showExport = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isExporting,
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export to Excel / CSV")
                    }
                }
            }

            BusyOverlay(
                visible = state.isExtracting || state.isExporting,
                label = if (state.isExtracting) "Extracting with Gemma 4…" else "Exporting…",
            )

            if (showExport) {
                IntakeExportSheet(
                    title = "Export families",
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
private fun InputModeRow(
    selected: FamilyIntakeViewModel.InputMode,
    onSelect: (FamilyIntakeViewModel.InputMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == FamilyIntakeViewModel.InputMode.Speak,
            onClick = { onSelect(FamilyIntakeViewModel.InputMode.Speak) },
            label = { Text("Speak") },
        )
        FilterChip(
            selected = selected == FamilyIntakeViewModel.InputMode.Type,
            onClick = { onSelect(FamilyIntakeViewModel.InputMode.Type) },
            label = { Text("Type") },
        )
        FilterChip(
            selected = selected == FamilyIntakeViewModel.InputMode.Photo,
            onClick = { onSelect(FamilyIntakeViewModel.InputMode.Photo) },
            label = { Text("Scan paper form") },
        )
    }
}

@Composable
private fun SpeakInput(
    state: FamilyIntakeViewModel.UiState,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onToggleListening: () -> Unit,
    onDescription: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = if (state.isListening && state.partialAsr.isNotBlank()) state.partialAsr else state.description,
                onValueChange = onDescription,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Speak naturally: \"Ahmed Mahmoud, 42, four kids, youngest is six with asthma, displaced from Aleppo three weeks ago…\"") },
                readOnly = state.isListening,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    if (micGranted) onToggleListening() else onRequestMic()
                },
            ) {
                Icon(
                    if (state.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state.isListening) "Stop" else "Speak")
            }
        }
    }
}

@Composable
private fun TypeInput(description: String, onDescription: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = description,
            onValueChange = onDescription,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .padding(16.dp),
            placeholder = { Text("Type a description of the family…") },
        )
    }
}

@Composable
private fun PhotoInput(hasImage: Boolean, onPick: () -> Unit, onCapture: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (hasImage) "Photo selected. Tap \"Extract with Gemma 4\" below."
                else "Photograph the family's paper registration sheet, or pick an existing photo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PhotoSourceRow(
                onGalleryPick = onPick,
                onCameraCapture = onCapture,
                galleryLabel = if (hasImage) "Change photo" else "Pick photo",
                cameraLabel = "Take photo",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftCard(
    draft: FamilyRecord,
    onUpdate: ((FamilyRecord) -> FamilyRecord) -> Unit,
    onSetDisplacement: (DisplacementStatus) -> Unit,
    onSetIncome: (IncomeLevel) -> Unit,
    onSave: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Review & edit", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = draft.headName,
                onValueChange = { v -> onUpdate { it.copy(headName = v) } },
                label = { Text("Head of household") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("Members", draft.memberCount, Modifier.weight(1f)) { v ->
                    onUpdate { it.copy(memberCount = v) }
                }
                IntField("Under 5", draft.childrenUnder5, Modifier.weight(1f)) { v ->
                    onUpdate { it.copy(childrenUnder5 = v) }
                }
                IntField("Elderly", draft.elderlyCount, Modifier.weight(1f)) { v ->
                    onUpdate { it.copy(elderlyCount = v) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = draft.hasPregnantMember,
                    onCheckedChange = { v -> onUpdate { it.copy(hasPregnantMember = v) } },
                )
                Spacer(Modifier.width(8.dp))
                Text("Pregnant member")
            }
            OutlinedTextField(
                value = draft.medicalConditions,
                onValueChange = { v -> onUpdate { it.copy(medicalConditions = v) } },
                label = { Text("Medical conditions") },
                placeholder = { Text("Comma-separated") },
                modifier = Modifier.fillMaxWidth(),
            )
            EnumChips(
                label = "Displacement",
                options = DisplacementStatus.values().filter { it != DisplacementStatus.Unknown },
                selected = draft.displacementStatus,
                display = { it.display },
                onSelect = onSetDisplacement,
            )
            EnumChips(
                label = "Income",
                options = IncomeLevel.values().filter { it != IncomeLevel.Unknown },
                selected = draft.incomeLevel,
                display = { it.display },
                onSelect = onSetIncome,
            )
            OutlinedTextField(
                value = draft.locationSector,
                onValueChange = { v -> onUpdate { it.copy(locationSector = v) } },
                label = { Text("Sector / camp / block") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.street,
                    onValueChange = { v -> onUpdate { it.copy(street = v) } },
                    label = { Text("Street") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.city,
                    onValueChange = { v -> onUpdate { it.copy(city = v) } },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> onUpdate { it.copy(notes = v) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            )
            draft.priorityScore?.let { score ->
                AssistChip(
                    onClick = {},
                    label = { Text("Priority ${draft.priorityLevel.display} ($score) — ${draft.priorityReason}") },
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save & start next family")
            }
        }
    }
}

@Composable
private fun IntField(label: String, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onValue(it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun <T> EnumChips(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(display(option)) },
                )
            }
        }
    }
}

@Composable
private fun CapturedRow(record: FamilyRecord, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(record.headName.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${record.memberCount} members · ${record.displacementStatus.display}" +
                        (record.priorityScore?.let { " · Priority $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IntakeExportSheet(
    title: String,
    onDismiss: () -> Unit,
    onPick: (ExportFormat) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Excel and CSV use the AidFlow web app's exact column names — no mapping needed on import.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FormatButton("Excel (.xlsx) — Recommended") { onPick(ExportFormat.Xlsx) }
            FormatButton("Spreadsheet (.csv)") { onPick(ExportFormat.Csv) }
            FormatButton("Plain text (.txt)") { onPick(ExportFormat.Txt) }
        }
    }
}

@Composable
private fun FormatButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(label)
        }
    }
}
