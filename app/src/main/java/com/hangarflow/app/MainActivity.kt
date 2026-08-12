package com.hangarflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.metrics.performance.JankStats
import com.hangarflow.app.data.OfflineCache
import com.hangarflow.app.AssignmentNotifier
import com.hangarflow.app.perf.HFPerfMonitor
import com.hangarflow.app.ui.shell.RootScreen
import com.hangarflow.app.ui.theme.HangarFlowTheme

class MainActivity : ComponentActivity() {
    // Lint's InvalidFragmentVersionForActivityResult is a false positive here:
    // this is a ComponentActivity (androidx.activity), where registerForActivityResult
    // is always valid regardless of the transitive Fragment version.
    @Suppress("InvalidFragmentVersionForActivityResult")
    private val requestNotificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { /* result ignored — user can retry from Settings */ }

    // Live perf watchdog — flags dropped/slow frames per screen.
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Swap back from the splash theme (black canvas) to the regular
        // theme before content is set so the window background matches
        // the Compose surface without a visible flicker.
        setTheme(R.style.Theme_HangarFlow)
        super.onCreate(savedInstanceState)
        OfflineCache.init(this)
        com.hangarflow.app.data.ShiftPersistence.init(this)
        AssignmentNotifier.init(this)
        // PDFBox-Android lazily loads fonts / assets from the APK; the
        // static PDFBoxResourceLoader has to see a Context first. Do it
        // up front so the first Navigator open is snappy.
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        askForNotificationPermission()
        enableEdgeToEdge()
        PushNotifications.ensureRegistered(this)
        com.hangarflow.app.push.FcmTokenManager.ensureRegistered(this)
        val hfVersion =
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        HFPerfMonitor.start(hfVersion)
        // Installed early so a crash during start-up is still captured. Sending
        // waits for an org — see below.
        com.hangarflow.app.perf.HFCrashReporter.install(applicationContext, hfVersion)
        setContent {
            HangarFlowTheme {
                // safeDrawing covers status bar, gesture/3-button nav,
                // and notch cutouts in one shot. Without applying the
                // innerPadding the Scaffold gives us, buttons at the
                // top/bottom of the screen render under the system
                // bars and look half-clipped.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        RootScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (jankStats == null) {
            jankStats = JankStats.createAndTrack(window) { frameData ->
                if (frameData.isJank) HFPerfMonitor.recordJank(frameData.frameDurationUiNanos / 1_000_000L)
            }
        }
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        jankStats?.isTrackingEnabled = false
    }

    private fun askForNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
