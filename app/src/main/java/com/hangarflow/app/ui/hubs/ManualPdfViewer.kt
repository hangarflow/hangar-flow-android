package com.hangarflow.app.ui.hubs

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.outlined.Search
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Bookmark
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.cloud.HFCloudSyncService
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.ManualCache
import com.hangarflow.app.data.model.HFManual
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.data.model.HFWorkLogStatus
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Work log detail screen — mirrors the iOS `IOSWorkLogExecutionSheet`:
 * header (title + citation + status), description, and an embedded PDF
 * preview scrolled to the referenced page. Tap the PDF (or the
 * "Open Full Manual" button) to push into full-screen mode.
 */
@Composable
fun WorkLogManualViewer(
    log: HFWorkLog,
    onClose: () -> Unit
) {
    val shopState by SharedStore.state.collectAsState()
    val context = LocalContext.current
    var isFullScreen by remember { mutableStateOf(false) }

    // Resolve HFManual. Priority:
    //   1. Real manual (sourceType manualPDF / manualText) for this
    //      plane whose filename matches the work log's source name —
    //      this is the right answer for hand-imported manuals.
    //   2. Any real manual on the plane — covers the common case where
    //      the work log was imported from a work package and the plane
    //      has one well-known maintenance manual.
    //   3. The exact source PDF (work package) the row was imported
    //      from — falls back to opening the work package itself when
    //      the plane has no proper manual yet.
    val manual = remember(log.id, shopState.manuals) {
        val sameTail: (com.hangarflow.app.data.model.HFManual) -> Boolean = {
            it.planeTailNumber?.equals(log.planeTailNumber, ignoreCase = true) == true
        }
        shopState.manuals.firstOrNull {
            sameTail(it) &&
                (it.sourceType == "manualPDF" || it.sourceType == "manualText") &&
                it.fileName.equals(log.manualSourceName, ignoreCase = true)
        }
            ?: shopState.manuals.firstOrNull {
                sameTail(it) &&
                    (it.sourceType == "manualPDF" || it.sourceType == "manualText")
            }
            ?: shopState.manuals.firstOrNull {
                sameTail(it) && it.fileName.equals(log.manualSourceName, ignoreCase = true)
            }
    }

    var localFile by remember { mutableStateOf<File?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manual?.id) {
        val m = manual ?: run {
            loadError = "No manual has been imported for this plane yet."
            return@LaunchedEffect
        }
        if (ManualCache.isCached(context, m)) {
            localFile = ManualCache.localFileFor(context, m)
            return@LaunchedEffect
        }
        runCatching { ManualCache.download(context, m) }
            .onSuccess { localFile = it }
            .onFailure { loadError = "Couldn't load the manual: ${it.message}" }
    }

    if (isFullScreen && localFile != null) {
        FullScreenPdf(
            file = localFile!!,
            initialPage = initialPageOf(log),
            logTitle = log.title,
            subtitle = fullScreenSubtitle(log),
            referenceCode = log.referenceCode?.takeIf { it.isNotBlank() },
            manualId = manual?.id,
            onClose = { isFullScreen = false }
        )
        return
    }

    WorkLogDetailScreen(
        log = log,
        manual = manual,
        localFile = localFile,
        loadError = loadError,
        onOpenFullManual = { isFullScreen = true },
        onClose = onClose
    )
}

// ---------- Detail view (non-fullscreen) ----------

@Composable
private fun WorkLogDetailScreen(
    log: HFWorkLog,
    manual: HFManual?,
    localFile: File?,
    loadError: String?,
    onOpenFullManual: () -> Unit,
    onClose: () -> Unit
) {
    val status = HFWorkLogStatus.fromRaw(log.status)
    val description = remember(log.id) { cleanDescription(log) }
    val citation = remember(log.id, manual) { buildCitation(log, manual) }
    var manualVisible by remember { mutableStateOf(true) }

    // Inputs state (time / initials / parts / photos)
    var minutesText by remember { mutableStateOf("") }
    // Initials come from the signed-in user's profile — auto-derived
    // from their display name (e.g. "Gabriel Barragan" → "G.B") so
    // sign-offs are always tied to the authenticated account.
    val shopState by SharedStore.state.collectAsState()
    val autoInitials = remember(shopState.currentUser) {
        val me = shopState.currentUser
        me?.initials?.takeIf { it.isNotBlank() }
            ?: me?.displayName?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.firstOrNull()?.uppercase() }
                ?.joinToString(".")
                ?.takeIf { it.isNotBlank() }
            ?: ""
    }
    var partsText by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<android.graphics.Bitmap>() }
    var savingWhat by remember { mutableStateOf<String?>(null) }  // "progress" | "signoff" | null

    val context = LocalContext.current
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) photos.add(bitmap) }
    val launchCameraWithPermission = com.hangarflow.app.ui.common.rememberCameraPermissionGate {
        cameraLauncher.launch(null)
    }

    val libraryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }.getOrNull()?.let { photos.add(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Close row
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.14f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = HFColors.OnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Header card
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.title.ifBlank { "Work log" },
                    color = HFColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (citation != null) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        citation,
                        color = HFColors.OnSurface.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            StatusPill(label = status.label, color = status.color)
        }

        Spacer(Modifier.size(16.dp))

        // Description — matches iOS `Work Order Update` / notes feel.
        if (description.isNotBlank()) {
            SectionCard(title = "Description") {
                Text(
                    text = description,
                    color = HFColors.OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.size(12.dp))
        }

        // Manual Viewer — tech can hide the PDF to focus on just the
        // work entry, toggle it back when they need to reference it.
        SectionCardWithToggle(
            title = "Manual Reference",
            toggleOn = manualVisible,
            onToggle = { manualVisible = !manualVisible }
        ) {
            if (!manualVisible) {
                Text(
                    text = "Manual hidden. Toggle above to show the reference PDF.",
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Compact "Manual Viewer" card — matches the iOS layout.
                // Tap opens the PDF full-screen. We deliberately don't
                // render the PDF inline anymore: the embedded pager
                // captured every scroll gesture in this region, which
                // made the form below it unreachable on tablet.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.06f))
                        .clickable(
                            enabled = loadError == null && localFile != null,
                            onClick = onOpenFullManual
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Manual Viewer",
                        color = HFColors.OnSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = when {
                            loadError != null -> loadError
                            localFile == null && manual == null -> "Finding the manual…"
                            localFile == null -> "Downloading ${manual?.fileName ?: "the manual"}…"
                            else -> "Tap to open the referenced manual page for this work log."
                        },
                        color = HFColors.OnSurface.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.size(14.dp))

        // Work order entry inputs — port of iOS `inputsCard`
        WorkLogInputsCard(
            minutes = minutesText,
            onMinutesChange = { minutesText = it.filter { c -> c.isDigit() }.take(4) },
            initials = autoInitials,
            onInitialsChange = null,
            parts = partsText,
            onPartsChange = { partsText = it },
            photos = photos,
            onRemovePhoto = { idx -> photos.removeAt(idx) },
            onOpenCamera = launchCameraWithPermission,
            onOpenLibrary = {
                libraryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            savingLabel = savingWhat,
            onSaveProgress = {
                savingWhat = "progress"
                com.hangarflow.app.data.SharedStore.saveWorkLogProgress(
                    workLog = log,
                    minutes = minutesText.toIntOrNull() ?: 0,
                    initials = autoInitials,
                    partsSummary = partsText,
                    signOff = false
                )
                // Optimistically clear the inputs so the tech sees the
                // save took effect. Realtime push will surface the new
                // time entry on the Time Card.
                minutesText = ""
                savingWhat = null
            },
            onSignOff = {
                savingWhat = "signoff"
                com.hangarflow.app.data.SharedStore.saveWorkLogProgress(
                    workLog = log,
                    minutes = minutesText.toIntOrNull() ?: 0,
                    initials = autoInitials,
                    partsSummary = partsText,
                    signOff = true
                )
                minutesText = ""
                savingWhat = null
            }
        )

        Spacer(Modifier.size(40.dp))
    }
}

// ---------- Full-screen PDF ----------

@Composable
internal fun FullScreenPdf(
    file: File,
    initialPage: Int,
    logTitle: String,
    subtitle: String?,
    referenceCode: String?,
    manualId: String? = null,
    onClose: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    // Use most of the screen height per page so each page fits on screen
    // the way PDFView does on iOS — user scrolls to flip pages.
    val pageHeight = (configuration.screenHeightDp - 140).dp.coerceAtLeast(480.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        PdfPagerContent(
            file = file,
            initialPage = initialPage,
            showIndicator = true,
            topPadding = 64.dp,
            bottomPadding = 48.dp,
            fixedPageHeight = pageHeight,
            referenceCode = referenceCode,
            manualId = manualId
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    logTitle.ifBlank { "Manual" },
                    color = HFColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = HFColors.OnSurface.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.14f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = HFColors.OnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------- Shared UI bits ----------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            title.uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp
        )
        Spacer(Modifier.size(8.dp))
        content()
    }
}

@Composable
private fun SectionCardWithToggle(
    title: String,
    toggleOn: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
                modifier = Modifier.weight(1f)
            )
            // Visual toggle — cyan when on (matches the squawk/photo
            // toggle pattern).
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 22.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (toggleOn) HFColors.StatusCyan else HFColors.OnSurface.copy(alpha = 0.18f))
                    .clickable(onClick = onToggle)
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(HFColors.OnSurface)
                        .align(if (toggleOn) Alignment.CenterEnd else Alignment.CenterStart)
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        content()
    }
}

// ---------- Work log inputs (iOS `inputsCard` port) ----------

@Composable
private fun WorkLogInputsCard(
    minutes: String,
    onMinutesChange: (String) -> Unit,
    initials: String,
    onInitialsChange: ((String) -> Unit)?,
    parts: String,
    onPartsChange: (String) -> Unit,
    photos: List<android.graphics.Bitmap>,
    onRemovePhoto: (Int) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenLibrary: () -> Unit,
    savingLabel: String?,
    onSaveProgress: () -> Unit,
    onSignOff: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InputField(
                label = "Time Spent",
                value = minutes,
                onChange = onMinutesChange,
                placeholder = "Minutes",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
            // Initials are derived from the authenticated user's name —
            // locked so sign-offs are always traceable to the right person.
            Column(modifier = Modifier.weight(1f)) {
                InputLabel("Sign-Off Initials")
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.04f))
                        .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Text(
                        initials.ifBlank { "—" },
                        color = HFColors.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        InputField(
            label = "Parts Used",
            value = parts,
            onChange = onPartsChange,
            placeholder = "e.g. PN 123-456 x2, PN 789-001",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
        )

        // Photos — single "+ Add" pill matching iOS. Tap opens a small
        // menu for Camera / Library so we keep both options without the
        // green/blue chip pair that didn't match the iPad aesthetic.
        Column {
            var showPhotoMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                InputLabel("Photos")
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(HFColors.OnSurface)
                            .clickable { showPhotoMenu = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Add,
                            contentDescription = null,
                            tint = HFColors.Background,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Add",
                            color = HFColors.Background,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showPhotoMenu,
                        onDismissRequest = { showPhotoMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Take Photo") },
                            onClick = { showPhotoMenu = false; onOpenCamera() },
                            leadingIcon = {
                                Icon(androidx.compose.material.icons.Icons.Outlined.PhotoCamera, null)
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Choose from Library") },
                            onClick = { showPhotoMenu = false; onOpenLibrary() },
                            leadingIcon = {
                                Icon(androidx.compose.material.icons.Icons.Outlined.PhotoLibrary, null)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            if (photos.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            androidx.compose.foundation.rememberScrollState()
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.forEachIndexed { idx, bitmap ->
                        Box {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(82.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(HFColors.StatusRed)
                                    .clickable { onRemovePhoto(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove",
                                    tint = HFColors.OnSurface,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = if (savingLabel == "progress") "Saving…" else "Save Progress",
                filled = true,
                color = HFColors.BrandInk,
                contentColor = HFColors.OnSurface,
                enabled = savingLabel == null,
                onClick = onSaveProgress
            )
            ActionButton(
                label = if (savingLabel == "signoff") "Signing off…" else "Sign Off Work Order",
                filled = true,
                color = HFColors.OnSurface.copy(alpha = 0.10f),
                contentColor = HFColors.OnSurface,
                enabled = savingLabel == null,
                onClick = onSignOff
            )
        }
    }
}

@Composable
private fun InputLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = HFColors.OnSurface.copy(alpha = 0.60f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp
    )
}

@Composable
private fun InputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: androidx.compose.ui.text.input.KeyboardType
) {
    Column(modifier = modifier) {
        InputLabel(label)
        Spacer(Modifier.size(6.dp))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            placeholder = {
                Text(
                    placeholder,
                    color = HFColors.OnSurface.copy(alpha = 0.35f),
                    fontSize = 13.sp
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
                unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
                focusedTextColor = HFColors.OnSurface,
                unfocusedTextColor = HFColors.OnSurface,
                cursorColor = HFColors.OnSurface
            )
        )
    }
}

@Composable
private fun AttachAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(
    label: String,
    filled: Boolean,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) color else HFColors.OnSurface.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.90f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = HFColors.BrandInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OpenFullManualButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.10f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Open Full Manual",
            color = HFColors.OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = HFColors.OnSurface,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ---------- PDF rendering ----------

/**
 * Vertical-scroll PDF renderer. Each page lays out at full width with
 * its natural aspect ratio so the whole page reads top-to-bottom the
 * way the manual was authored. Continuous scroll beats swipe-to-page
 * on tablets — mechanics flip through 150-page sections fast.
 */
@Composable
private fun PdfPagerContent(
    file: File,
    initialPage: Int,
    showIndicator: Boolean,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    /** If set, each page renders at this exact height with letterboxed
     *  Fit scaling — same behavior as iOS PDFView autoScales. */
    fixedPageHeight: androidx.compose.ui.unit.Dp? = null,
    /** Reference code to pin in the corner (e.g. "00-00") — iPad shows
     *  this next to the page number so techs know the chapter at a glance. */
    referenceCode: String? = null,
    /** When provided, tapping the green page badge opens the bookmarks
     *  sheet (ATA-grouped references for this manual) so the user can
     *  jump to any chapter without paging through the whole PDF. */
    manualId: String? = null
) {
    var renderer by remember(file.absolutePath) { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember(file.absolutePath) { mutableStateOf(0) }
    // Per-page aspect ratio (width / height) cached after the first open
    // so the LazyColumn can reserve the right space for each item.
    val pageAspects = remember(file.absolutePath) { mutableStateMapOf<Int, Float>() }

    DisposableEffect(file.absolutePath) {
        // Open is cheap; just parses the xref. Don't touch individual
        // pages here — that's expensive on big manuals and would block
        // the main thread (ANR on the 159 MB Pilatus AMM).
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        renderer = r
        pageCount = r.pageCount
        onDispose {
            r.close()
            pfd.close()
        }
    }

    // Scan page dimensions off the main thread. Each openPage/close pair
    // is fast individually but adds up to multi-second blocking on a
    // thousand-page AMM, so we do it on IO and let the LazyColumn fill
    // in aspect ratios as they arrive.
    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            synchronized(r) {
                for (i in 0 until r.pageCount) {
                    runCatching {
                        val p = r.openPage(i)
                        pageAspects[i] = if (p.height > 0) p.width.toFloat() / p.height else 0.75f
                        p.close()
                    }
                }
            }
        }
    }

    if (pageCount == 0 || renderer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HFColors.OnSurface, strokeWidth = 2.dp)
        }
        return
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, pageCount - 1)
    )
    val currentVisiblePage = remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex + 1
        }
    }

    // Snap fling so each swipe lands cleanly on the next page — tablet
    // users expect a carousel feel, not free-scroll that stops halfway.
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(count = pageCount, key = { it }) { pageIndex ->
                // Per-page pinch/pan state. Lets techs zoom in on a
                // diagram without leaving the work-log flow.
                var scale by remember(pageIndex) { mutableStateOf(1f) }
                var offsetX by remember(pageIndex) { mutableStateOf(0f) }
                var offsetY by remember(pageIndex) { mutableStateOf(0f) }
                val aspect = pageAspects[pageIndex] ?: 0.77f
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .aspectRatio(aspect)
                        .pointerInput(pageIndex) {
                            // Only claim gestures when 2+ fingers are
                            // down (pinch/pan). Single-finger drags are
                            // left alone so the parent LazyColumn can
                            // scroll the manual vertically.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointers = event.changes.count { it.pressed }
                                    if (pointers >= 2) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        PdfPage(
                            renderer = renderer!!,
                            pageIndex = pageIndex,
                            // FillWidth uses the full carousel width —
                            // page looks zoomed-in like iPad instead of
                            // letterboxed with empty side gutters.
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }

        val bookmarksOpenState = remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        if (showIndicator) {
            var badgeModifier: Modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(HFColors.BrandInk.copy(alpha = 0.78f))
                .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.55f), RoundedCornerShape(100.dp))
            if (manualId != null) {
                badgeModifier = badgeModifier.clickable { bookmarksOpenState.value = true }
            }
            badgeModifier = badgeModifier.padding(horizontal = 12.dp, vertical = 5.dp)

            Row(modifier = badgeModifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bookmark,
                    contentDescription = null,
                    tint = HFColors.StatusGreen,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Page ${currentVisiblePage.value} / $pageCount",
                    color = HFColors.StatusGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (bookmarksOpenState.value && manualId != null) {
            ManualBookmarksSheet(
                manualId = manualId,
                pdfFile = file,
                currentPage = currentVisiblePage.value,
                totalPages = pageCount,
                onDismiss = { bookmarksOpenState.value = false },
                onJumpToPage = { page ->
                    bookmarksOpenState.value = false
                    val idx = (page - 1).coerceIn(0, pageCount - 1)
                    scope.launch { listState.animateScrollToItem(idx) }
                }
            )
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    contentScale: ContentScale = ContentScale.FillWidth
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                runCatching {
                    val page = renderer.openPage(pageIndex)
                    // Render at high resolution so the page reads sharp
                    // when zoomed and on tablet displays. 2800 ≈ ~320dpi
                    // for a letter-size page — matches iPad PDFView clarity.
                    val targetWidth = 2800
                    val scale = targetWidth.toFloat() / page.width
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // PdfRenderer leaves the bitmap transparent for any
                    // pixel the PDF doesn't explicitly paint. Since PDFs
                    // assume a white paper background and our app canvas
                    // is black, text rendered in black becomes invisible.
                    // Pre-fill the bitmap with white so the page reads
                    // like paper. Matches iOS PDFView default behavior.
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }.getOrNull()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            // FillWidth keeps the full page visible: width fills the
            // container, height scales proportionally. Fit was showing
            // just the top slice on portrait pages with tall preview
            // containers — this matches iOS's continuous-scroll PDF.
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(color = HFColors.OnSurface, strokeWidth = 2.dp)
        }
    }
}

// ---------- helpers ----------

private fun initialPageOf(log: HFWorkLog): Int =
    (log.manualPageStart ?: 1).coerceAtLeast(1) - 1

private fun fullScreenSubtitle(log: HFWorkLog): String? = listOfNotNull(
    log.manualSourceName?.takeIf { it.isNotBlank() },
    log.manualPageStart?.let { "p. $it" }
).joinToString(" · ").takeIf { it.isNotBlank() }

/**
 * Manual citation line — e.g. "Pilatus AMM • Ch 27-30 • p. 42-45" —
 * built from whatever reference fields are populated on the log. Mirrors
 * iOS `hfManualCitationText()` output.
 */
private fun buildCitation(log: HFWorkLog, manual: HFManual?): String? {
    val parts = mutableListOf<String>()
    log.manualSourceName?.takeIf { it.isNotBlank() }?.let { parts += it }
    log.referenceCode?.takeIf { it.isNotBlank() }?.let { parts += it }
    val pageRange = when {
        log.manualPageStart != null && log.manualPageEnd != null && log.manualPageEnd != log.manualPageStart ->
            "p. ${log.manualPageStart}-${log.manualPageEnd}"
        log.manualPageStart != null -> "p. ${log.manualPageStart}"
        else -> null
    }
    pageRange?.let { parts += it }
    if (parts.isEmpty() && manual != null && manual.fileName.isNotBlank()) {
        parts += manual.fileName
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}

/**
 * Strip the iOS workflow metadata block from the `details` field so we
 * show only the human description. Mirrors iOS `hfVisibleWorkLogDetails`
 * — markers are literal brackets, surrounding payload is base64-ish JSON
 * (parts consumed, photo IDs, etc.) and would dump as garbage on screen.
 */
// ---------- Bookmarks sheet ----------

/**
 * ATA-chapter bucket used to group bookmarks. The `hf_manual_references`
 * table stores reference codes like `27-41-00`, `71-00-00` — we split
 * on the first token and map it to a discipline name tech already know.
 */
private data class AtaBucket(val title: String, val accent: androidx.compose.ui.graphics.Color)

private val AtaMap: Map<String, AtaBucket> = mapOf(
    "05" to AtaBucket("Inspection", HFColors.StatusRed),
    "06" to AtaBucket("Dimensions", HFColors.OnSurface),
    "07" to AtaBucket("Airframe", HFColors.StatusBlue),
    "10" to AtaBucket("Parking & Storage", HFColors.OnSurface),
    "11" to AtaBucket("Placards", HFColors.OnSurface),
    "12" to AtaBucket("Servicing", HFColors.StatusBlue),
    "20" to AtaBucket("Standard Practices", HFColors.OnSurface),
    "21" to AtaBucket("Air Conditioning", HFColors.StatusCyan),
    "22" to AtaBucket("Autoflight", HFColors.StatusGreen),
    "23" to AtaBucket("Avionics", HFColors.StatusGreen),
    "24" to AtaBucket("Electrical Power", HFColors.StatusOrange),
    "25" to AtaBucket("Interior", HFColors.StatusPurple),
    "26" to AtaBucket("Fire Protection", HFColors.StatusRed),
    "27" to AtaBucket("Flight Controls", HFColors.StatusBlue),
    "28" to AtaBucket("Fuel", HFColors.StatusOrange),
    "29" to AtaBucket("Hydraulic", HFColors.StatusBlue),
    "30" to AtaBucket("Ice / Rain", HFColors.StatusCyan),
    "31" to AtaBucket("Indicating", HFColors.OnSurface),
    "32" to AtaBucket("Landing Gear", HFColors.StatusBlue),
    "33" to AtaBucket("Lights", HFColors.StatusYellow),
    "34" to AtaBucket("Navigation", HFColors.StatusGreen),
    "35" to AtaBucket("Oxygen", HFColors.StatusCyan),
    "51" to AtaBucket("Structures", HFColors.StatusBlue),
    "52" to AtaBucket("Doors", HFColors.StatusBlue),
    "53" to AtaBucket("Fuselage", HFColors.StatusBlue),
    "54" to AtaBucket("Nacelles", HFColors.StatusBlue),
    "55" to AtaBucket("Stabilizers", HFColors.StatusBlue),
    "56" to AtaBucket("Windows", HFColors.StatusBlue),
    "57" to AtaBucket("Wings", HFColors.StatusBlue),
    "61" to AtaBucket("Propeller", HFColors.StatusCyan),
    "71" to AtaBucket("Engine", HFColors.StatusOrange),
    "72" to AtaBucket("Engine", HFColors.StatusOrange),
    "73" to AtaBucket("Engine Fuel", HFColors.StatusOrange),
    "74" to AtaBucket("Engine Ignition", HFColors.StatusOrange),
    "75" to AtaBucket("Engine Air", HFColors.StatusOrange),
    "76" to AtaBucket("Engine Controls", HFColors.StatusOrange),
    "77" to AtaBucket("Engine Indicating", HFColors.StatusOrange),
    "78" to AtaBucket("Engine Exhaust", HFColors.StatusOrange),
    "79" to AtaBucket("Engine Oil", HFColors.StatusOrange)
)

private fun bucketForReference(code: String?): AtaBucket {
    val prefix = code?.trim()?.take(2) ?: ""
    return AtaMap[prefix] ?: AtaBucket("General", HFColors.OnSurfaceMuted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualBookmarksSheet(
    manualId: String,
    pdfFile: File,
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    val authState by AuthManager.state.collectAsState()
    val orgId = authState.orgId
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Source of truth for titles is the PDF's own outline. iOS reads
    // the same structure out of PDFDocument.outlineRoot — extracting
    // via PDFBox gets us the real section / subject names the author
    // baked into the file ("AIR VEHICLE GENERAL", "LIST OF APPLICABLE
    // DATA MODULES", etc.) instead of raw ATA codes.
    var outline by remember(pdfFile.absolutePath) {
        mutableStateOf<List<com.hangarflow.app.data.cloud.PdfOutlineEntry>?>(null)
    }
    var loading by remember(manualId) { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    // Text search engine — loads page text once, then searches in memory.
    val searcher = remember(pdfFile.absolutePath) {
        com.hangarflow.app.data.cloud.PdfTextSearcher()
    }
    var searchResults by remember { mutableStateOf<List<com.hangarflow.app.data.cloud.PdfSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(pdfFile.absolutePath) {
        loading = true
        outline = com.hangarflow.app.data.cloud.PdfOutlineReader.read(pdfFile)
        searcher.ensureLoaded(pdfFile)
        loading = false
    }

    // Debounced text search — fires 250ms after typing stops.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { searchResults = emptyList(); return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(250)
        searchResults = searcher.search(q)
        searching = false
    }

    // Mode: if query ≥ 2 chars → search results; else → outline tree.
    val isSearchMode = query.trim().length >= 2

    // Outline tree — only built once, filtered by query for Mode A when
    // the user types just one character (not yet search mode).
    val tree: List<NavNode> = remember(outline, query) {
        val ol = outline
        if (ol != null && ol.isNotEmpty()) buildTreeFromOutline(ol, if (isSearchMode) "" else query)
        else emptyList()
    }
    val hasOutline = (outline?.isNotEmpty() == true)

    // Track expand state keyed by full path so stable across recompositions.
    val expanded = remember(manualId) { mutableStateMapOf<String, Boolean>() }
    // When searching, auto-expand every branch so matches are visible.
    LaunchedEffect(query, tree) {
        if (query.isNotBlank()) expandAll(tree, expanded)
    }

    // Flatten the tree to a visible list respecting expand state. This
    // is what LazyColumn iterates over — one composable per visible row.
    val visible: List<NavVisibleRow> = remember(tree, expanded.toMap()) {
        flattenVisible(tree, expanded)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Background,
        contentColor = HFColors.OnSurface,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title row: "Navigator" + subtitle + Close
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Navigator",
                        color = HFColors.OnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Page $currentPage of $totalPages",
                        color = HFColors.OnSurface.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    "Close",
                    color = HFColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            // Search
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, null, tint = HFColors.OnSurface.copy(alpha = 0.55f))
                    },
                    placeholder = {
                        Text(
                            "Search the manual…",
                            color = HFColors.OnSurface.copy(alpha = 0.40f),
                            fontSize = 13.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                        unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                        focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
                        unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
                        focusedTextColor = HFColors.OnSurface,
                        unfocusedTextColor = HFColors.OnSurface,
                        cursorColor = HFColors.OnSurface
                    )
                )
            }

            Spacer(Modifier.size(6.dp))
            Text(
                "BOOKMARKS / SECTIONS  ·  ${outline?.size ?: 0}",
                color = HFColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
                when {
                    loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HFColors.OnSurface, strokeWidth = 2.dp)
                    }

                    // Mode B: search results
                    isSearchMode -> {
                        if (searching) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = HFColors.OnSurface,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        "Searching…",
                                        color = HFColors.OnSurface.copy(alpha = 0.60f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else if (searchResults.isEmpty()) {
                            Text(
                                "No matches for \"${query.trim()}\".",
                                color = HFColors.OnSurface.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(20.dp)
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                searchResults.forEach { hit ->
                                    item(key = "sr-${hit.pageNumber}-${hit.snippet.hashCode()}") {
                                        SearchHitRow(
                                            hit = hit,
                                            onClick = { onJumpToPage(hit.pageNumber) }
                                        )
                                    }
                                }
                                item { Spacer(Modifier.height(60.dp)) }
                            }
                        }
                    }

                    // Mode A: outline tree
                    !hasOutline -> Text(
                        "This PDF has no embedded bookmarks. Use search above to jump to any word.",
                        color = HFColors.OnSurface.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(20.dp)
                    )
                    visible.isEmpty() -> Text(
                        "No matches for \"$query\".",
                        color = HFColors.OnSurface.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(20.dp)
                    )
                    else -> androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        visible.forEach { row ->
                            item(key = row.node.path) {
                                NavRow(
                                    row = row,
                                    onToggle = {
                                        expanded[row.node.path] =
                                            !(expanded[row.node.path] ?: false)
                                    },
                                    onJump = { onJumpToPage(row.node.pageStart ?: 1) }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(60.dp)) }
                    }
                }

                // Footer pill
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(HFColors.StatusGreen.copy(alpha = 0.22f))
                        .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.55f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Page $currentPage / $totalPages",
                        color = HFColors.StatusGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ---------- Tree model ----------

/** One node in the navigator tree. `path` is the full dash-joined code
 *  used both as a map key (expand state) and a stable composable key. */
private data class NavNode(
    val key: String,
    val path: String,
    val title: String,
    val pageStart: Int?,
    val level: Int,
    val children: List<NavNode>
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

private data class NavVisibleRow(
    val node: NavNode,
    val expanded: Boolean
)

/** Split an ATA code on "-" / whitespace into non-empty tokens. */
private fun splitCode(code: String?): List<String> =
    code?.trim().orEmpty().split(Regex("[-\\s]+")).filter { it.isNotBlank() }

/**
 * Build the tree from a flat list of references. Two passes:
 *   1. For each reference build its full path ("00-10-20") and walk it,
 *      grouping siblings at each depth.
 *   2. Decorate each node with a human title taken from the deepest
 *      reference that ends exactly there, falling back to the shared
 *      prefix when only subtrees exist.
 */
private fun buildNavTree(
    refs: List<HFCloudSyncService.ManualSearchHit>
): List<NavNode> {
    data class Bucket(
        val key: String,
        val path: String,
        val level: Int,
        val children: MutableMap<String, Bucket> = linkedMapOf(),
        var exactTitle: String? = null,
        var exactPage: Int? = null
    )

    val root = Bucket(key = "", path = "", level = -1)
    for (hit in refs) {
        val tokens = splitCode(hit.referenceCode)
        if (tokens.isEmpty()) continue
        var cursor = root
        val acc = StringBuilder()
        tokens.forEachIndexed { idx, token ->
            if (idx > 0) acc.append("-")
            acc.append(token)
            val path = acc.toString()
            val child = cursor.children.getOrPut(token) {
                Bucket(key = token, path = path, level = idx)
            }
            if (idx == tokens.lastIndex) {
                // Only set the exact page/title once — first reference
                // that lands exactly on this code wins (stable ordering
                // from the caller's sort).
                if (child.exactPage == null) {
                    child.exactPage = hit.pageStart
                    child.exactTitle = hit.title?.takeIf { it.isNotBlank() }
                }
            }
            cursor = child
        }
    }

    fun toNode(b: Bucket): NavNode {
        val kids = b.children.values.sortedBy { it.key }.map(::toNode)
        val title = b.exactTitle
            ?: kids.firstOrNull()?.title
            ?: b.key
        val page = b.exactPage
            ?: kids.firstNotNullOfOrNull { it.pageStart }
        return NavNode(
            key = b.key,
            path = b.path,
            title = title,
            pageStart = page,
            level = b.level,
            children = kids
        )
    }

    return root.children.values.sortedBy { it.key }.map(::toNode)
}

/**
 * Rebuild the nested NavNode tree from the flat (label, page, depth)
 * list that `PdfOutlineReader` returns. Walks the list using a depth
 * stack so any number of levels works. Applies the query as a filter —
 * branches are kept if any descendant matches.
 */
private fun buildTreeFromOutline(
    entries: List<com.hangarflow.app.data.cloud.PdfOutlineEntry>,
    query: String
): List<NavNode> {
    data class MutableNode(
        val title: String,
        val page: Int?,
        val depth: Int,
        val path: String,
        val children: MutableList<MutableNode> = mutableListOf()
    )

    val rootChildren = mutableListOf<MutableNode>()
    val stack = ArrayDeque<MutableNode>()
    var serial = 0
    for (e in entries) {
        // Pop the stack back to the correct parent depth.
        while (stack.isNotEmpty() && stack.last().depth >= e.depth) stack.removeLast()
        val parentPath = stack.lastOrNull()?.path.orEmpty()
        val path = if (parentPath.isEmpty()) "n${serial++}" else "$parentPath/n${serial++}"
        val node = MutableNode(
            title = e.label,
            page = e.pageNumber,
            depth = e.depth,
            path = path
        )
        if (stack.isEmpty()) rootChildren += node
        else stack.last().children += node
        stack.addLast(node)
    }

    // Optional query filter — keep branches whose own title or any
    // descendant's title matches, pruning the rest.
    val q = query.trim().lowercase()
    fun matches(n: MutableNode): Boolean {
        if (q.isEmpty()) return true
        if (n.title.lowercase().contains(q)) return true
        return n.children.any(::matches)
    }

    fun toNav(n: MutableNode): NavNode {
        val kids = n.children.filter(::matches).map(::toNav)
        // Split "00 - AIR VEHICLE GENERAL" into key + title so the row
        // renders the numeric prefix separately (matches the iPad).
        val raw = n.title
        val sep = raw.indexOf(" - ").takeIf { it > 0 } ?: raw.indexOf(" – ")
        val (key, pretty) = if (sep != null && sep > 0 && sep < raw.length)
            raw.substring(0, sep).trim() to raw.substring(sep + 3).trim()
        else "" to raw.trim()
        return NavNode(
            key = key,
            path = n.path,
            title = pretty,
            pageStart = n.page,
            level = n.depth,
            children = kids
        )
    }

    return rootChildren.filter(::matches).map(::toNav)
}

private fun expandAll(nodes: List<NavNode>, state: MutableMap<String, Boolean>) {
    nodes.forEach { n ->
        if (n.children.isNotEmpty()) {
            state[n.path] = true
            expandAll(n.children, state)
        }
    }
}

private fun flattenVisible(
    nodes: List<NavNode>,
    expanded: Map<String, Boolean>
): List<NavVisibleRow> {
    val out = mutableListOf<NavVisibleRow>()
    fun walk(ns: List<NavNode>) {
        for (n in ns) {
            val isExpanded = expanded[n.path] == true
            out.add(NavVisibleRow(n, isExpanded))
            if (isExpanded && n.children.isNotEmpty()) walk(n.children)
        }
    }
    walk(nodes)
    return out
}

@Composable
private fun NavRow(
    row: NavVisibleRow,
    onToggle: () -> Unit,
    onJump: () -> Unit
) {
    val node = row.node
    val indent = (node.level * 16).dp
    val hasChildren = !node.isLeaf
    // Leaf nodes jump to page on tap; branch nodes toggle expand.
    val onTap: () -> Unit = if (hasChildren) onToggle else onJump

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(start = indent + 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand chevron column (fixed 16dp so titles align across levels)
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (hasChildren) {
                Text(
                    if (row.expanded) "▾" else "▸",
                    color = HFColors.OnSurface.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(HFColors.OnSurface.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        // Code prefix like "00" or "00-10" — muted, then the title.
        if (node.key.isNotBlank() && !node.key.equals(node.title, ignoreCase = true)) {
            Text(
                "${node.key} ",
                color = HFColors.OnSurface.copy(alpha = 0.70f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "- ",
                color = HFColors.OnSurface.copy(alpha = 0.35f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            node.title.uppercase(),
            color = HFColors.OnSurface,
            fontSize = 12.sp,
            fontWeight = if (hasChildren) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 3
        )
        // Page pill on the right (always shown for leaves, also shown
        // for branches if we know a first-page target).
        node.pageStart?.let { page ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "P$page",
                    color = HFColors.OnSurface.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    // Subtle bottom divider so the tree reads like the iPad's list
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
    )
}

@Composable
private fun SearchHitRow(
    hit: com.hangarflow.app.data.cloud.PdfSearchHit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(HFColors.StatusGreen.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "P${hit.pageNumber}",
                color = HFColors.StatusGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            hit.snippet,
            color = HFColors.OnSurface.copy(alpha = 0.80f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            modifier = Modifier.weight(1f)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
    )
}

private fun cleanDescription(log: HFWorkLog): String {
    val body = log.details
    val startMarker = "[HF_WORKFLOW_METADATA_BEGIN]"
    val endMarker = "[HF_WORKFLOW_METADATA_END]"
    val startIdx = body.indexOf(startMarker)
    val endIdx = body.indexOf(endMarker)
    val cleaned = if (startIdx >= 0 && endIdx > startIdx) {
        body.substring(0, startIdx) + body.substring(endIdx + endMarker.length)
    } else {
        body
    }
    val trimmed = cleaned.trim()
    return if (trimmed.isNotBlank()) trimmed
    else log.referenceParagraph?.trim().orEmpty()
}
