package com.aidflow.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aidflow.pro.R
import com.aidflow.pro.ui.ModelSetupViewModel
import com.aidflow.pro.ui.appViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupScreen(onReady: () -> Unit) {
    val vm: ModelSetupViewModel = appViewModel()
    val state by vm.state.collectAsState(initial = ModelSetupViewModel.SetupState.NotStarted)
    val allowMetered by vm.allowMetered.collectAsState()
    var hasStarted by rememberSaveable { mutableStateOf(false) }

    // Auto-start on entry: if the model was already downloaded in a previous session
    // this is a fast no-op of the download phase and just kicks off engine init.
    // For a first install it still requires the user to tap "Download model" to
    // accept the ~3 GB transfer over their data plan, so we only auto-start when
    // we already have the bits on disk.
    LaunchedEffect(Unit) {
        if (vm.isModelOnDisk() && !hasStarted) {
            hasStarted = true
            vm.start()
        }
    }

    LaunchedEffect(state) {
        if (state is ModelSetupViewModel.SetupState.Ready) onReady()
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResRes(R.string.setup_title)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResRes(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResRes(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResRes(R.string.setup_body), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResRes(R.string.setup_storage_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = allowMetered, onCheckedChange = vm::setAllowMetered)
                        Spacer(Modifier.width(12.dp))
                        Text("Allow mobile data", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            ProgressArea(state)
            Spacer(Modifier.height(24.dp))
            ActionArea(
                state = state,
                onStart = {
                    hasStarted = true
                    vm.start()
                },
                onRetry = vm::retry,
                hasStarted = hasStarted,
            )
        }
    }
}

@Composable
private fun ProgressArea(state: ModelSetupViewModel.SetupState) {
    when (state) {
        is ModelSetupViewModel.SetupState.Downloading -> Column {
            LinearProgressIndicator(
                progress = { state.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatBytes(state.bytes)} / ${if (state.total > 0) formatBytes(state.total) else "?"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is ModelSetupViewModel.SetupState.Verifying ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResRes(R.string.setup_verifying))
            }
        is ModelSetupViewModel.SetupState.LoadingEngine ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResRes(R.string.setup_initializing))
            }
        is ModelSetupViewModel.SetupState.Error -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Text(
                state.message,
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        is ModelSetupViewModel.SetupState.Ready -> Text(stringResRes(R.string.setup_ready))
        ModelSetupViewModel.SetupState.NotStarted -> Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun ActionArea(
    state: ModelSetupViewModel.SetupState,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    hasStarted: Boolean,
) {
    when (state) {
        is ModelSetupViewModel.SetupState.Error ->
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResRes(R.string.setup_retry))
            }
        is ModelSetupViewModel.SetupState.NotStarted -> if (!hasStarted) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResRes(R.string.setup_download))
            }
        }
        else -> Unit
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var idx = 0
    while (value >= 1024 && idx < units.size - 1) { value /= 1024; idx++ }
    return "%.1f %s".format(value, units[idx])
}

@Composable
private fun stringResRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
