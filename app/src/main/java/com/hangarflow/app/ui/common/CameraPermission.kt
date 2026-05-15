package com.hangarflow.app.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a lambda that checks the CAMERA permission before calling
 * [onGranted]. If already granted, fires immediately. If not, requests
 * it — and fires [onGranted] only if the user taps "Allow".
 *
 * Usage:
 * ```
 * val launchCamera = rememberCameraPermissionGate { cameraLauncher.launch(null) }
 * Button(onClick = launchCamera) { Text("Camera") }
 * ```
 */
@Composable
fun rememberCameraPermissionGate(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted()
    }
    return remember(context, onGranted) {
        {
            val already = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (already) onGranted()
            else permLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
