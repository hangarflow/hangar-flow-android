package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.model.HFSquawk
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import androidx.compose.ui.platform.LocalContext

/**
 * Squawks hub. Lists every open squawk with its plane tail, title,
 * notes, status pill, and any attached photos. Photos are pulled from
 * the private `squawk-photos` bucket via short-lived signed URLs and
 * rendered with Coil so the list stays snappy.
 */
@Composable
fun SquawksHub() {
    var showCreate by remember { mutableStateOf(false) }
    // Full-screen photo viewer state.
    var viewingPhotoPaths by remember { mutableStateOf<List<String>?>(null) }
    var viewingPhotoIndex by remember { mutableStateOf(0) }

    if (viewingPhotoPaths != null) {
        com.hangarflow.app.ui.common.FullScreenPhotoViewer(
            photoPaths = viewingPhotoPaths!!,
            initialIndex = viewingPhotoIndex,
            onClose = { viewingPhotoPaths = null }
        )
        return
    }
    if (showCreate) {
        CreateSquawkSheet(onDismiss = { showCreate = false })
        return
    }
    HFPullToRefreshHost {
        SquawksHubContent(
            onOpenCreate = { showCreate = true },
            onOpenPhoto = { paths, index ->
                viewingPhotoPaths = paths
                viewingPhotoIndex = index
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SquawksHubContent(
    onOpenCreate: () -> Unit,
    onOpenPhoto: (paths: List<String>, index: Int) -> Unit
) {
    val state by SharedStore.state.collectAsState()
    val squawks = remember(state.squawks) {
        state.squawks.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
    }

    val filters = listOf("All", "Open", "In Progress", "Waiting", "Resolved")
    var selectedFilter by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var statusSheetFor by remember { mutableStateOf<HFSquawk?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<HFSquawk?>(null) }
    val deleteScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val filtered = remember(squawks, selectedFilter, search) {
        val byStatus = when (selectedFilter) {
            "Open" -> squawks.filter { it.status == "open" }
            "In Progress" -> squawks.filter { it.status == "inProgress" }
            "Waiting" -> squawks.filter { it.status == "waitingOnParts" }
            "Resolved" -> squawks.filter { it.status == "resolved" }
            else -> squawks
        }
        val q = search.trim().lowercase()
        if (q.isEmpty()) byStatus
        else byStatus.filter {
            it.title.lowercase().contains(q) ||
                it.notes.lowercase().contains(q) ||
                it.planeTailNumber.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Primary CTA — clean white "Apple" button, matching the iOS
        // squawk-builder primary action.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HFColors.BrandWhite)
                .clickable(onClick = onOpenCreate)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "New Squawk",
                color = HFColors.BrandInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.size(16.dp))

        // Filter chips — full-width capsule row, white text, subtle fill,
        // matching the iOS plane-filter chip treatment.
        SectionCaption("FILTER")
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                SquawkFilterChip(
                    label = filter,
                    isSelected = filter == selectedFilter,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        // Search bar — filters by title / notes / tail, mirroring iOS.
        SquawkSearchBar(query = search, onChange = { search = it }, onClear = { search = "" })

        Spacer(Modifier.size(14.dp))

        Text(
            text = "${filtered.size} item${if (filtered.size == 1) "" else "s"}",
            color = HFColors.OnSurface.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.size(12.dp))

        if (filtered.isEmpty()) {
            IOSPlaceholderPanel(
                message = if (squawks.isEmpty())
                    "No squawks yet — a tech files them from the field."
                else "No squawks match the current filter."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { squawk ->
                    SquawkCard(
                        squawk = squawk,
                        onTapStatus = { statusSheetFor = squawk },
                        onDelete = { deleteConfirmFor = squawk },
                        onOpenPhoto = onOpenPhoto
                    )
                }
            }
        }
    }

    statusSheetFor?.let { squawk ->
        ModalBottomSheet(
            onDismissRequest = { statusSheetFor = null },
            sheetState = sheetState,
            containerColor = HFColors.Surface,
            contentColor = HFColors.OnSurface
        ) {
            SquawkStatusPickerSheet(
                current = squawk.status,
                onPick = { newStatus ->
                    if (newStatus == "convertedToTask") {
                        SharedStore.convertSquawkToTask(squawk)
                    } else {
                        SharedStore.updateSquawkStatus(squawk.id, newStatus)
                    }
                    statusSheetFor = null
                }
            )
        }
    }

    deleteConfirmFor?.let { squawk ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            containerColor = HFColors.Surface,
            title = { Text("Delete squawk?", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${squawk.title.ifBlank { "Untitled squawk" }}\" will be removed for everyone. This can't be undone.",
                    color = HFColors.OnSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = squawk.id
                    deleteScope.launch { SharedStore.deleteSquawk(id) }
                    deleteConfirmFor = null
                }) { Text("Delete", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteConfirmFor = null }) {
                    Text("Cancel", color = HFColors.OnSurface.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
private fun SquawkStatusPickerSheet(current: String, onPick: (String) -> Unit) {
    // "convertedToTask" is in the picker so techs can spawn a Task from
    // the squawk without leaving the sheet — same flow iOS uses. The
    // parent handles the side effect of actually creating the HFTask.
    val options = listOf("open", "inProgress", "waitingOnParts", "convertedToTask", "resolved", "deferred")
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            "Change Status".uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.size(12.dp))
        options.forEach { option ->
            val (label, color) = statusPresentation(option)
            val isCurrent = option == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) color.copy(alpha = 0.14f) else HFColors.OnSurface.copy(alpha = 0.04f))
                    .border(
                        1.dp,
                        if (isCurrent) color.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onPick(option) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.size(10.dp))
                Text(
                    label,
                    color = if (isCurrent) color else HFColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.size(8.dp))
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        color = HFColors.OnSurface.copy(alpha = 0.5f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )
}

@Composable
private fun SquawkSearchBar(query: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = HFColors.OnSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(
                    "Search squawks",
                    color = HFColors.OnSurface.copy(alpha = 0.4f),
                    fontSize = 14.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = HFColors.OnSurface,
                unfocusedTextColor = HFColors.OnSurface,
                cursorColor = HFColors.OnSurface
            )
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear",
                    tint = HFColors.OnSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SquawkFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    // Capsule chip — white text, subtle fill, brighter when selected.
    // Mirrors the iOS plane-filter chip row.
    val bg = HFColors.OnSurface.copy(alpha = if (isSelected) 0.16f else 0.06f)
    val border = HFColors.OnSurface.copy(alpha = if (isSelected) 0.5f else 0.12f)
    val fg = HFColors.OnSurface.copy(alpha = if (isSelected) 1f else 0.75f)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SquawkCard(
    squawk: HFSquawk,
    onTapStatus: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhoto: (paths: List<String>, index: Int) -> Unit
) {
    val (statusLabel, statusColor) = statusPresentation(squawk.status)

    // Clean black-&-white card — subtle white fill + hairline border,
    // matching the iOS squawk list. Status shows as a small accented
    // pill, not a full-card tint.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = squawk.title.ifBlank { "Untitled squawk" },
                color = HFColors.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // Anyone (including techs) can delete a squawk.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Delete", color = HFColors.StatusRed.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaPill(text = squawk.planeTailNumber.ifBlank { "No tail" })
            if (!squawk.reportedByUserName.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                MetaPill(text = "by ${squawk.reportedByUserName}")
            }
            if (!squawk.assignedUserName.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                MetaPill(text = "→ ${squawk.assignedUserName}")
            }
        }

        if (squawk.notes.isNotBlank()) {
            Spacer(Modifier.size(10.dp))
            Text(
                text = squawk.notes,
                color = HFColors.OnSurface.copy(alpha = 0.72f),
                fontSize = 13.sp,
                maxLines = 4
            )
        }

        if (squawk.photoPaths.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            // Explicit in-app affordance — opens the photo viewer (not a browser).
            Text(
                "Open attached Photos (${squawk.photoPaths.size})",
                color = HFColors.StatusCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPhoto(squawk.photoPaths, 0) }
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                squawk.photoPaths.forEachIndexed { idx, path ->
                    SquawkPhotoThumb(
                        path = path,
                        onClick = { onOpenPhoto(squawk.photoPaths, idx) }
                    )
                }
            }
        }

        // Status footer — tap the pill to change status. Neutral capsule
        // with a colored dot + label, the iOS card-footer treatment.
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(color = statusColor, label = statusLabel, onClick = onTapStatus)
        }
    }
}

@Composable
private fun SquawkPhotoThumb(path: String, onClick: () -> Unit) {
    val cloud = remember { HFCloudSyncService() }
    var signedUrl by remember(path) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(path) {
        signedUrl = runCatching { cloud.signedSquawkPhotoURL(path) }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        if (signedUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(signedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Squawk photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    // Neutral metadata chip — quiet, monochrome, matching iOS card meta.
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(HFColors.OnSurface.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(color: Color, label: String, onClick: () -> Unit) {
    // Neutral capsule with a colored status dot — clean & monochrome,
    // tappable to change the status.
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(HFColors.OnSurface.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = HFColors.OnSurface.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun statusPresentation(raw: String): Pair<String, Color> = when (raw) {
    "open" -> "Open" to HFColors.StatusBlue
    "inProgress" -> "In Progress" to HFColors.StatusYellow
    "waitingOnParts" -> "Waiting" to HFColors.StatusOrange
    "resolved" -> "Resolved" to HFColors.StatusGreen
    "deferred" -> "Deferred" to HFColors.OnSurface.copy(alpha = 0.5f)
    "convertedToTask" -> "Converted" to HFColors.StatusPurple
    else -> raw.replaceFirstChar { it.titlecase() } to HFColors.OnSurface.copy(alpha = 0.6f)
}
