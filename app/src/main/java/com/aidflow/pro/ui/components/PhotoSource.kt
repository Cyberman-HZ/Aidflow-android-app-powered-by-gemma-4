package com.aidflow.pro.ui.components

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

/**
 * Camera-capture helper. Returns a function that you can call from any UI event
 * handler to take a photo:
 *
 * - First call requests the runtime CAMERA permission if not yet granted; the
 *   camera launches automatically once the user accepts.
 * - Subsequent calls launch the camera directly.
 * - On success, [onCaptured] receives a `content://` URI that the rest of the
 *   pipeline (ML Kit, Gemma vision, FileProvider) can read.
 *
 * Captured photos live in `cacheDir/captures/` so they are automatically
 * cleaned up by Android when storage is tight.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberCameraCapture(onCaptured: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingLaunch by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingUri
        pendingUri = null
        if (success && uri != null) onCaptured(uri)
    }

    val permission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(permission.status.isGranted, pendingLaunch) {
        if (permission.status.isGranted && pendingLaunch) {
            pendingLaunch = false
            launchCamera(context, launcher) { uri -> pendingUri = uri }
        }
    }

    return remember(permission, launcher, context) {
        {
            if (permission.status.isGranted) {
                launchCamera(context, launcher) { uri -> pendingUri = uri }
            } else {
                pendingLaunch = true
                permission.launchPermissionRequest()
            }
        }
    }
}

private fun launchCamera(
    context: Context,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Uri, Boolean>,
    onUriCreated: (Uri) -> Unit,
) {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    onUriCreated(uri)
    launcher.launch(uri)
}

/**
 * Two-button row: "Pick from gallery" + "Take photo". Use this everywhere we
 * need to source an image.
 */
@Composable
fun PhotoSourceRow(
    onGalleryPick: () -> Unit,
    onCameraCapture: () -> Unit,
    modifier: Modifier = Modifier,
    galleryLabel: String = "Pick photo",
    cameraLabel: String = "Take photo",
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onGalleryPick,
            modifier = Modifier
                .weight(1f),
        ) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(galleryLabel)
        }
        FilledTonalButton(
            onClick = onCameraCapture,
            modifier = Modifier
                .weight(1f),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(cameraLabel)
        }
    }
}
