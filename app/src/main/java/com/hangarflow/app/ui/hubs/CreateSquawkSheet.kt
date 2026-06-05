package com.hangarflow.app.ui.hubs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Full-screen sheet for creating a new squawk from Android. Plane
 * picker + title + notes + camera + library photo attach. On save it
 * resizes each photo, uploads to the `squawk-photos` bucket, inserts
 * the `hf_squawks` row, and emits an org event so the Mac admin sees
 * it appear within ~1 second.
 */
@Composable
fun CreateSquawkSheet(onDismiss: () -> Unit) {
    val shopState by SharedStore.state.collectAsState()
    val authState by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cloud = remember { HFCloudSyncService() }

    var selectedPlane by remember { mutableStateOf<HFPlane?>(shopState.planes.firstOrNull()) }
    var planeMenuExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<Bitmap>() }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var needsParts by remember { mutableStateOf(false) }
    var requestedPart by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("normal") }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) photos.add(bitmap)
    }
    val launchCameraWithPermission = com.hangarflow.app.ui.common.rememberCameraPermissionGate {
        cameraLauncher.launch(null)
    }

    val libraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        uris.forEach { uri ->
            decodeUriToBitmap(context, uri)?.let { photos.add(it) }
        }
    }

    val canSave = selectedPlane != null &&
        title.isNotBlank() &&
        !isSaving &&
        authState.orgId != null

    // Single save path shared by "Save" and "Save & add another". When
    // keepOpen is true we clear the entry fields but keep the plane
    // selected so a tech can stack squawks during a walk-around without
    // re-opening the sheet (the mobile bulk-entry pattern).
    val doSave: (Boolean) -> Unit = doSave@{ keepOpen ->
        val plane = selectedPlane ?: return@doSave
        val orgId = authState.orgId ?: return@doSave
        isSaving = true
        saveError = null
        val snapshotPhotos = photos.toList()
        scope.launch {
            try {
                val compressed = withContext(Dispatchers.Default) {
                    snapshotPhotos.map { compressForUpload(it) }
                }
                val squawkIdPlaceholder = java.util.UUID.randomUUID().toString()
                val uploadedPaths = compressed.map { bytes ->
                    cloud.uploadSquawkPhoto(
                        data = bytes,
                        orgId = orgId,
                        squawkId = squawkIdPlaceholder
                    )
                }
                val newSquawkId = cloud.createSquawk(
                    orgId = orgId,
                    planeId = plane.id,
                    planeTailNumber = plane.tailNumber,
                    title = title.trim(),
                    notes = notes.trim(),
                    reportedByUserId = shopState.currentUser?.id,
                    reportedByUserName = shopState.currentUser?.displayName,
                    photoPaths = uploadedPaths,
                    sourceDevice = SharedStore.deviceIdentifier()
                )
                if (needsParts && (requestedPart.isNotBlank() || title.isNotBlank())) {
                    cloud.createPartRequest(
                        orgId = orgId,
                        squawkId = newSquawkId,
                        planeId = plane.id,
                        planeTailNumber = plane.tailNumber,
                        title = title.trim(),
                        requestedPart = requestedPart.trim(),
                        urgency = urgency,
                        requestedBy = shopState.currentUser?.displayName
                    )
                }
                SharedStore.refresh()
                if (keepOpen) {
                    title = ""
                    notes = ""
                    photos.clear()
                    needsParts = false
                    requestedPart = ""
                } else {
                    onDismiss()
                }
            } catch (t: Throwable) {
                saveError = t.message ?: "Failed to save squawk."
            } finally {
                isSaving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        // Header matches IOSHubHeader visually
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("New Squawk", color = HFColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Report a discrepancy. Admins see it within seconds.",
                    color = HFColors.OnSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.10f))
                    .clickable(onClick = onDismiss),
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Plane picker
            SectionLabel("Plane")
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.06f))
                        .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .clickable { planeMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPlane?.let { "${it.tailNumber} — ${it.displayName.ifBlank { "Unnamed" }}" }
                            ?: "Pick a plane",
                        color = if (selectedPlane == null) HFColors.OnSurface.copy(alpha = 0.45f) else HFColors.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                DropdownMenu(
                    expanded = planeMenuExpanded,
                    onDismissRequest = { planeMenuExpanded = false }
                ) {
                    shopState.planes.forEach { plane ->
                        DropdownMenuItem(
                            text = { Text("${plane.tailNumber} — ${plane.displayName.ifBlank { "Unnamed" }}") },
                            onClick = {
                                selectedPlane = plane
                                planeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            SectionLabel("Title")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = hfFieldColors()
            )

            SectionLabel("Notes")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors = hfFieldColors()
            )

            // Needs Parts block (matches iOS squawk builder)
            NeedsPartsSection(
                needsParts = needsParts,
                onToggle = { needsParts = it },
                requestedPart = requestedPart,
                onRequestedPartChange = { requestedPart = it },
                urgency = urgency,
                onUrgencyChange = { urgency = it }
            )

            SectionLabel("Photos")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachButton(
                    icon = Icons.Outlined.Camera,
                    label = "Camera",
                    color = HFColors.StatusGreen,
                    onClick = launchCameraWithPermission,
                    modifier = Modifier.weight(1f)
                )
                AttachButton(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "Library",
                    color = HFColors.StatusBlue,
                    onClick = {
                        libraryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (photos.isEmpty()) {
                Text(
                    "No photos attached yet.",
                    color = HFColors.OnSurface.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.forEachIndexed { idx, bitmap ->
                        Box {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(HFColors.StatusRed)
                                    .clickable { photos.removeAt(idx) },
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

            saveError?.let { msg ->
                Text(msg, color = HFColors.StatusRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.size(4.dp))

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canSave) HFColors.BrandWhite
                        else HFColors.OnSurface.copy(alpha = 0.15f)
                    )
                    .clickable(enabled = canSave) { doSave(false) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = HFColors.BrandInk,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = if (photos.isEmpty()) "Save Squawk" else "Upload & Save",
                        color = if (canSave) HFColors.BrandInk else HFColors.OnSurface.copy(alpha = 0.4f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Save & add another — commit this squawk, clear the fields, keep
            // the plane selected so the next one is one tap away.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HFColors.StatusOrange.copy(alpha = 0.14f))
                    .border(1.dp, HFColors.StatusOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable(enabled = canSave) { doSave(true) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Save & add another",
                    color = if (canSave) HFColors.StatusOrange else HFColors.OnSurface.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun NeedsPartsSection(
    needsParts: Boolean,
    onToggle: (Boolean) -> Unit,
    requestedPart: String,
    onRequestedPartChange: (String) -> Unit,
    urgency: String,
    onUrgencyChange: (String) -> Unit
) {
    val shopState by SharedStore.state.collectAsState()
    Column {
        // Needs Parts toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (needsParts) HFColors.StatusOrange.copy(alpha = 0.12f)
                    else HFColors.OnSurface.copy(alpha = 0.06f)
                )
                .border(
                    1.dp,
                    if (needsParts) HFColors.StatusOrange.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.10f),
                    RoundedCornerShape(12.dp)
                )
                .clickable { onToggle(!needsParts) }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Needs Parts",
                    color = if (needsParts) HFColors.StatusOrange else HFColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (needsParts) "Adds this to the parts-to-order queue" else "Toggle on to request a part",
                    color = HFColors.OnSurface.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            // Visual toggle indicator
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 24.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        if (needsParts) HFColors.StatusOrange else HFColors.OnSurface.copy(alpha = 0.15f)
                    )
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(HFColors.OnSurface)
                        .align(if (needsParts) Alignment.CenterEnd else Alignment.CenterStart)
                )
            }
        }

        if (needsParts) {
            Spacer(Modifier.size(10.dp))
            SectionLabel("Requested Part")
            OutlinedTextField(
                value = requestedPart,
                onValueChange = onRequestedPartChange,
                placeholder = {
                    Text(
                        "Part number or description",
                        color = HFColors.OnSurface.copy(alpha = 0.40f),
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = hfFieldColors()
            )

            // Live "in stock?" check against the shared inventory.
            val q = requestedPart.trim().lowercase()
            if (q.isNotEmpty()) {
                val matches = shopState.partLocations.filter {
                    it.partName.lowercase().contains(q) || it.partNumber.lowercase().contains(q)
                }
                val match = matches.firstOrNull { it.quantity > 0 } ?: matches.firstOrNull()
                val inStock = (match?.quantity ?: 0) > 0
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (inStock) HFColors.StatusGreen else HFColors.StatusOrange)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (inStock) "In stock: ${match!!.quantity} @ ${match.location.ifBlank { "—" }}"
                        else "Not in stock — will be added to Parts to Order",
                        color = if (inStock) HFColors.StatusGreen else HFColors.StatusOrange,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.size(10.dp))
            SectionLabel("Urgency")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrgencyPill("LOW", "low", urgency == "low", HFColors.StatusGreen, onUrgencyChange, Modifier.weight(1f))
                UrgencyPill("NORMAL", "normal", urgency == "normal", HFColors.StatusOrange, onUrgencyChange, Modifier.weight(1f))
                UrgencyPill("AOG", "urgentAOG", urgency == "urgentAOG", HFColors.StatusRed, onUrgencyChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UrgencyPill(
    label: String,
    value: String,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) color.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.04f)
    val border = if (isSelected) color else HFColors.OnSurface.copy(alpha = 0.10f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onPick(value) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) color else HFColors.OnSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

// ---------- helpers ----------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = HFColors.OnSurface.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AttachButton(
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
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun hfFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
    focusedTextColor = HFColors.OnSurface,
    unfocusedTextColor = HFColors.OnSurface,
    cursorColor = HFColors.OnSurface
)

private fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()

/** Resize down to 1800px on the long edge, then encode to JPEG 72 quality. */
private fun compressForUpload(bitmap: Bitmap): ByteArray {
    val max = 1800
    val scaled = if (bitmap.width > max || bitmap.height > max) {
        val ratio = max.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
    return out.toByteArray()
}

