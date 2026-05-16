package com.aidflow.pro.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
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
 * Camera + auto-crop capture via Google's on-device document scanner.
 *
 * Compared to the bare-bones [rememberCameraCapture]:
 *   • Lens-switch button (front/back) in the scanner UI.
 *   • Flash toggle (auto / on / off).
 *   • Live edge detection that highlights the document/subject.
 *   • Auto-cropping + perspective correction (FULL mode) or raw capture (BASE mode).
 *   • Manual corner adjustment if the auto-detection guess is wrong.
 *   • Color / grayscale / B&W filter options before confirming.
 *
 * Mode guidance:
 *   • [GmsDocumentScannerOptions.SCANNER_MODE_FULL] — paper forms and documents.
 *     Auto-crops the detected page and warps to a clean rectangle.
 *   • [GmsDocumentScannerOptions.SCANNER_MODE_BASE] — anything that isn't a
 *     document (e.g. supply photos). Provides camera UI without forcing a crop.
 *
 * Returns a callable that opens the scanner. Callbacks deliver a `content://`
 * URI living in Google Play services' temporary storage; ML Kit / Coil /
 * ContentResolver can read it as-is (Google grants temporary read permission).
 *
 * Requires Google Play services. On a device without GMS, callers should fall
 * back to [rememberCameraCapture] (the system camera intent path).
 */
@Composable
fun rememberDocumentScanner(
    onCaptured: (Uri) -> Unit,
    scannerMode: Int = GmsDocumentScannerOptions.SCANNER_MODE_FULL,
    onError: (String) -> Unit = { Log.w("PhotoSource", "Doc scanner: $it") },
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uri = scanResult?.pages?.firstOrNull()?.imageUri
            if (uri != null) onCaptured(uri) else onError("Scanner returned no pages")
        }
        // resultCode == RESULT_CANCELED is a normal user-cancel; no error
    }

    return remember(launcher, activity, scannerMode) {
        {
            if (activity == null) {
                onError("No Activity host for the document scanner")
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(false)
                    .setPageLimit(1)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(scannerMode)
                    .build()
                GmsDocumentScanning.getClient(options)
                    .getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener { t ->
                        onError(t.message ?: "Couldn't start the document scanner")
                    }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
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
