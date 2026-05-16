package com.aidflow.pro.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aidflow.pro.intake.IdentifiedItem
import com.aidflow.pro.intake.ItemCategory
import com.aidflow.pro.ui.ItemsViewModel
import com.aidflow.pro.ui.appViewModel
import com.aidflow.pro.ui.components.BusyOverlay
import com.aidflow.pro.ui.components.PhotoSourceRow
import com.aidflow.pro.ui.components.rememberCameraCapture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(onBack: () -> Unit) {
    val vm: ItemsViewModel = appViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExport by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.setImage(uri)
            vm.identify(context)
        }
    }
    val captureImage = rememberCameraCapture { uri ->
        vm.setImage(uri)
        vm.identify(context)
    }

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
                title = { Text("Identify items") },
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
                    "Point your camera at relief supplies — Gemma 4 vision lists every item it can identify, with a category and quantity estimate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PhotoSourceRow(
                    onGalleryPick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraCapture = captureImage,
                    galleryLabel = if (state.items.isEmpty()) "Pick photo" else "Add from gallery",
                    cameraLabel = "Take photo",
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.items.isEmpty() && !state.isIdentifying) {
                    Card { Text(
                        "No items captured yet.",
                        Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) }
                }

                state.items.forEach { item ->
                    ItemRow(
                        item = item,
                        onUpdate = { update -> vm.update(item.id, update) },
                        onRemove = { vm.remove(item.id) },
                    )
                }

                if (state.items.isNotEmpty()) {
                    Button(
                        onClick = { showExport = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isExporting,
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export inventory to Excel / CSV")
                    }
                }
            }

            BusyOverlay(
                visible = state.isIdentifying || state.isExporting,
                label = if (state.isIdentifying) "Identifying items with Gemma 4 vision…" else "Exporting…",
            )

            if (showExport) {
                IntakeExportSheet(
                    title = "Export items",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemRow(
    item: IdentifiedItem,
    onUpdate: ((IdentifiedItem) -> IdentifiedItem) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.quantity?.toString().orEmpty(),
                    onValueChange = { v -> onUpdate { it.copy(quantity = v.toIntOrNull()) } },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.unit,
                    onValueChange = { v -> onUpdate { it.copy(unit = v) } },
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            CategoryChips(
                selected = item.category,
                onSelect = { cat -> onUpdate { it.copy(category = cat) } },
            )
            if (item.notes.isNotBlank()) {
                Text(
                    item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    selected: ItemCategory,
    onSelect: (ItemCategory) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ItemCategory.values().forEach { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(c.display) },
            )
        }
    }
}
