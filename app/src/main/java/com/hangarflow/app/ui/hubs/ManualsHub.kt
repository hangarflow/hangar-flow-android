package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.ManualCache
import com.hangarflow.app.data.model.HFManual
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * Manuals hub — lists every plane's manuals (filtered to real manual
 * rows, not work packages), offers a download-for-offline button, and
 * opens the PDF in Android's system viewer via a signed URL once the
 * file is cached locally (or the signed URL directly if not cached yet).
 */
@Composable
fun ManualsHub() {
    HFPullToRefreshHost { ManualsHubContent() }
}

@Composable
private fun ManualsHubContent() {
    val state by SharedStore.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // In-app PDF viewer state. When non-null we render the full-screen
    // Compose PDF viewer on top of the list — no external app launch.
    var viewingFile by remember { mutableStateOf<java.io.File?>(null) }
    var viewingManual by remember { mutableStateOf<HFManual?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    val authState by com.hangarflow.app.auth.AuthManager.state.collectAsState()
    var confirmPurge by remember { mutableStateOf<HFManual?>(null) }

    // Same filter as the Live View tile: only real manuals, deduped.
    val manuals = remember(state.manuals) {
        state.manuals
            .filter { it.sourceType == "manualPDF" || it.sourceType == "manualText" }
            .distinctBy { "${it.planeTailNumber?.uppercase()}:${it.fileName.lowercase()}" }
            .sortedWith(compareBy({ it.planeTailNumber?.uppercase() ?: "" }, { it.fileName.lowercase() }))
    }

    // Download state per manual id — starts nothing, transitions
    // Idle → Downloading → Cached (or Error).
    val downloadStates = remember { mutableStateMapOf<String, DownloadState>() }

    // Seed the map from disk so cached manuals show a checkmark right away.
    LaunchedForContext(context, manuals) {
        manuals.forEach { manual ->
            if (ManualCache.isCached(context, manual)) {
                downloadStates[manual.id] = DownloadState.Cached
            }
        }
    }

    if (manuals.isEmpty()) {
        // Empty state — mirrors iOS `IOSInfoPanel("No manuals yet")`:
        // a rounded card with subtle border rather than bare centered text.
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.05f))
                    .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(
                    "NO MANUALS YET",
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Admins import manuals from the desktop. They'll show up here once they sync.",
                    color = HFColors.OnSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section caption — matches iOS "Plane Manuals" header above the list.
        item(key = "__plane_manuals_caption") {
            Text(
                "PLANE MANUALS",
                color = HFColors.OnSurface.copy(alpha = 0.62f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
        }
        items(manuals, key = { it.id }) { manual ->
            val downloadState = downloadStates[manual.id] ?: DownloadState.Idle
            ManualRow(
                manual = manual,
                downloadState = downloadState,
                onTap = {
                    openError = null
                    // If we already have it cached, open instantly.
                    if (ManualCache.isCached(context, manual)) {
                        viewingFile = ManualCache.localFileFor(context, manual)
                        viewingManual = manual
                        return@ManualRow
                    }
                    // Otherwise download then open in-app. No external
                    // viewer handoff — the whole experience stays inside
                    // Hangar Flow so the user doesn't bounce out.
                    downloadStates[manual.id] = DownloadState.Downloading
                    scope.launch {
                        runCatching { ManualCache.download(context, manual) }
                            .onSuccess { file ->
                                downloadStates[manual.id] = DownloadState.Cached
                                viewingFile = file
                                viewingManual = manual
                            }
                            .onFailure { err ->
                                downloadStates[manual.id] =
                                    DownloadState.Error(err.message ?: "Download failed")
                                openError = err.message ?: "Couldn't open manual."
                            }
                    }
                },
                onDownload = {
                    if (downloadState is DownloadState.Downloading) return@ManualRow
                    downloadStates[manual.id] = DownloadState.Downloading
                    scope.launch {
                        runCatching { ManualCache.download(context, manual) }
                            .onSuccess { downloadStates[manual.id] = DownloadState.Cached }
                            .onFailure { err ->
                                downloadStates[manual.id] =
                                    DownloadState.Error(err.message ?: "Download failed")
                            }
                    }
                },
                onPurge = if (authState.isAdmin) {
                    { confirmPurge = manual }
                } else null
            )
        }
    }

    confirmPurge?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmPurge = null },
            containerColor = HFColors.Surface,
            title = { Text("Purge ${target.fileName}?", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently deletes the manual file for everyone in the org. Indexed references are kept so it can be re-attached later.",
                    color = HFColors.OnSurface, fontSize = 13.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = target.id
                    confirmPurge = null
                    scope.launch { SharedStore.purgeManual(id) }
                }) { Text("Purge", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmPurge = null }) { Text("Cancel") }
            }
        )
    }

    // In-app full-screen PDF viewer — sits above the list when a
    // manual is opened. Close button returns to this hub; no Intent
    // handoff to Android's PDF app.
    val vf = viewingFile
    val vm = viewingManual
    if (vf != null && vm != null) {
        FullScreenPdf(
            file = vf,
            initialPage = 0,
            logTitle = vm.title.takeIf { it.isNotBlank() } ?: vm.fileName,
            subtitle = vm.planeTailNumber,
            referenceCode = null,
            manualId = vm.id,
            onClose = {
                viewingFile = null
                viewingManual = null
            }
        )
    }

    openError?.let { msg ->
        androidx.compose.material3.Text(
            text = msg,
            color = HFColors.StatusRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ManualRow(
    manual: HFManual,
    downloadState: DownloadState,
    onTap: () -> Unit,
    onDownload: () -> Unit,
    onPurge: (() -> Unit)? = null
) {
    val cached = downloadState is DownloadState.Cached
    // iOS uses Color.indigo for the manual card accent. StatusPurple is the
    // closest token in HFColors and matches the iPad/iPhone manual rows.
    val accent = HFColors.StatusPurple

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Match IOSManualPlaneRow: 16-radius indigo-tinted card with a
            // 1pt indigo stroke (fill 0.08, stroke 0.35), 12dp inner padding.
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tappable left area — opens PDF
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 44dp icon tile, indigo 0.22 fill, 12-radius — like iOS book.pages.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                val tail = manual.planeTailNumber?.takeIf { it.isNotBlank() } ?: "—"
                Text(
                    text = tail,
                    color = HFColors.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    text = manual.title.takeIf { it.isNotBlank() } ?: manual.fileName,
                    color = HFColors.OnSurface.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        // Right-hand download/state button — 40dp circle like iOS, white
        // fill 0.12 when cached / 0.08 otherwise, with a 22dp glyph centered.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (cached) HFColors.OnSurface.copy(alpha = 0.12f)
                    else HFColors.OnSurface.copy(alpha = 0.08f)
                )
                .clickable(enabled = !cached, onClick = onDownload),
            contentAlignment = Alignment.Center
        ) {
            when (downloadState) {
                DownloadState.Downloading -> CircularProgressIndicator(
                    color = HFColors.OnSurface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                DownloadState.Cached -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Saved offline",
                    tint = HFColors.StatusGreen,
                    modifier = Modifier.size(22.dp)
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download for offline",
                    tint = HFColors.OnSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (onPurge != null) {
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HFColors.StatusRed.copy(alpha = 0.10f))
                    .clickable(onClick = onPurge)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Purge", color = HFColors.StatusRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (downloadState is DownloadState.Error) {
        Spacer(Modifier.size(4.dp))
        Text(
            text = friendlyDownloadError(downloadState.message),
            color = HFColors.StatusRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Map a raw download/sign error to a one-line user-facing message.
 * Keeps random JSON / stack-trace text from leaking into the card.
 */
private fun friendlyDownloadError(raw: String): String {
    val lower = raw.lowercase()
    return when {
        "bucket" in lower && "not found" in lower ->
            "Couldn't find the manual file in cloud storage."
        "401" in lower || "unauthor" in lower ->
            "You don't have access to this manual."
        "403" in lower || "forbidden" in lower ->
            "Permission denied. Ask the admin for access."
        "404" in lower -> "Manual file is missing — re-import on the desktop."
        "network" in lower || "unable" in lower || "host" in lower ->
            "No connection. Try again with internet."
        else -> "Couldn't download the manual. Try again."
    }
}

// ---------- helpers ----------

private sealed class DownloadState {
    object Idle : DownloadState()
    object Downloading : DownloadState()
    object Cached : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Composable
private fun LaunchedForContext(
    context: android.content.Context,
    manuals: List<HFManual>,
    block: () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(manuals.map { it.id }) {
        block()
    }
}

