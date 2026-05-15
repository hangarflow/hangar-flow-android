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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.data.model.HFWorkCategory
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.data.model.HFWorkLogStatus
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * Plane detail screen. Two-level flow — first shows a grid of work
 * categories (Airframe / Engine / Propeller / Avionics / Interior /
 * Inspection / General), mirroring the iPad layout. Tap a category to
 * drill into that plane's work logs filtered to that category.
 */
@Composable
fun PlaneDetailView(
    plane: HFPlane,
    onBack: () -> Unit,
    onOpenWorkLog: (HFWorkLog) -> Unit
) {
    val state by SharedStore.state.collectAsState()
    val authState by com.hangarflow.app.auth.AuthManager.state.collectAsState()
    var selectedCategory by remember { mutableStateOf<HFWorkCategory?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val accent = remember(plane.id) {
        runCatching {
            plane.outlineHex
                ?.removePrefix("#")
                ?.takeIf { it.length == 6 }
                ?.let { Color("FF$it".toLong(16)) }
        }.getOrNull() ?: HFColors.OnSurface
    }

    val planeWorkLogs = remember(state.workLogs, plane.id) {
        state.workLogs.filter { it.planeId == plane.id }
    }

    if (selectedCategory != null) {
        CategoryWorkLogList(
            plane = plane,
            accent = accent,
            category = selectedCategory!!,
            workLogs = planeWorkLogs,
            onBack = { selectedCategory = null },
            onBackToPlanes = onBack,
            onOpenWorkLog = onOpenWorkLog
        )
        return
    }

    // Edit + delete dialogs for admin
    if (showEditDialog) {
        EditPlaneSheet(plane = plane, onDismiss = { showEditDialog = false })
    }
    if (showDeleteDialog) {
        DeletePlaneSheet(plane = plane, onDismiss = { showDeleteDialog = false; onBack() })
    }

    CategoryGrid(
        plane = plane,
        accent = accent,
        workLogs = planeWorkLogs,
        isAdmin = authState.isAdmin,
        onBack = onBack,
        onEdit = { showEditDialog = true },
        onDelete = { showDeleteDialog = true },
        onSelectCategory = { selectedCategory = it }
    )
}

// ---------- Category grid (first screen) ----------

@Composable
private fun CategoryGrid(
    plane: HFPlane,
    accent: Color,
    workLogs: List<HFWorkLog>,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelectCategory: (HFWorkCategory) -> Unit
) {
    // Seven tiles — iPad shows the six maintenance disciplines + General.
    // Each card shows the plane's open-work count for that discipline.
    val tiles = listOf(
        CategoryTile(HFWorkCategory.Airframe, Icons.Outlined.Build, HFColors.StatusBlue),
        CategoryTile(HFWorkCategory.Engine, Icons.Outlined.Settings, HFColors.StatusOrange),
        CategoryTile(HFWorkCategory.Propeller, Icons.Rounded.Flight, HFColors.StatusCyan),
        CategoryTile(HFWorkCategory.Avionics, Icons.Outlined.Memory, HFColors.StatusGreen),
        CategoryTile(
            HFWorkCategory.Interior,
            Icons.Rounded.AirlineSeatReclineNormal,
            HFColors.StatusPurple
        ),
        CategoryTile(HFWorkCategory.Inspection, Icons.Outlined.VerifiedUser, HFColors.StatusRed),
        CategoryTile(HFWorkCategory.General, Icons.AutoMirrored.Outlined.Article, HFColors.OnSurfaceMuted)
    )

    val counts = remember(workLogs) {
        workLogs
            .filter { HFWorkLogStatus.fromRaw(it.status) != HFWorkLogStatus.Done }
            .groupingBy { HFWorkCategory.fromRaw(it.category) }
            .eachCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        // Top bar: back button + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.10f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = HFColors.OnSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "All planes",
                color = HFColors.OnSurface.copy(alpha = 0.70f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onBack)
            )
        }

        // Plane title header — big and bold, matches iPad "Pilatus PC12".
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                plane.tailNumber,
                color = HFColors.OnSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            if (plane.displayName.isNotBlank() && plane.displayName != plane.tailNumber) {
                Spacer(Modifier.size(4.dp))
                Text(
                    plane.displayName,
                    color = HFColors.OnSurface.copy(alpha = 0.68f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            // Accent bar under the title — subtle visual for the plane's
            // outline color so the user ties category + plane together.
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 3.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(accent)
            )
            if (isAdmin) {
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(HFColors.OnSurface.copy(alpha = 0.06f))
                            .clickable(onClick = onEdit)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Edit", color = HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(HFColors.StatusRed.copy(alpha = 0.10f))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Delete", color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        Spacer(Modifier.size(22.dp))

        // Category grid — 2 columns
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = tiles.chunked(2), key = { it.first().category.raw }) { rowPair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowPair.forEach { tile ->
                        CategoryCard(
                            tile = tile,
                            openCount = counts[tile.category] ?: 0,
                            onClick = { onSelectCategory(tile.category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowPair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item { Spacer(Modifier.size(40.dp)) }
        }
    }
}

private data class CategoryTile(
    val category: HFWorkCategory,
    val icon: ImageVector,
    val accent: Color
)

@Composable
private fun CategoryCard(
    tile: CategoryTile,
    openCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.5.dp, tile.accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tile.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = tile.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = HFColors.OnSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            tile.category.label,
            color = HFColors.OnSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = when (openCount) {
                0 -> "No open work"
                1 -> "1 open item"
                else -> "$openCount open items"
            },
            color = HFColors.OnSurface.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------- Work logs for a single category (second screen) ----------

@Composable
private fun CategoryWorkLogList(
    plane: HFPlane,
    accent: Color,
    category: HFWorkCategory,
    workLogs: List<HFWorkLog>,
    onBack: () -> Unit,
    onBackToPlanes: () -> Unit,
    onOpenWorkLog: (HFWorkLog) -> Unit
) {
    val filtered = remember(workLogs, category) {
        workLogs
            .filter { HFWorkCategory.fromRaw(it.category) == category }
            .sortedWith(
                compareBy(
                    { HFWorkLogStatus.fromRaw(it.status) == HFWorkLogStatus.Done },
                    { it.updatedAt ?: "" }
                )
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        // Top bar: back to categories + breadcrumb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.10f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = HFColors.OnSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = plane.tailNumber,
                color = HFColors.OnSurface.copy(alpha = 0.70f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onBack)
            )
            Text(
                text = " · ${category.label}",
                color = HFColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Category title header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                category.label,
                color = HFColors.OnSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(2.dp))
            Text(
                "${filtered.size} work log${if (filtered.size == 1) "" else "s"} · ${plane.tailNumber}",
                color = HFColors.OnSurface.copy(alpha = 0.62f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 3.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(accent)
            )
        }

        Spacer(Modifier.size(16.dp))

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No ${category.label.lowercase()} work logs",
                    color = HFColors.OnSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Nothing in this category for ${plane.tailNumber} yet.",
                    color = HFColors.OnSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { log ->
                PlaneWorkLogRow(log = log, onClick = { onOpenWorkLog(log) })
            }
            item { Spacer(Modifier.size(40.dp)) }
        }
    }
}

// ---------- Shared row ----------

@Composable
private fun PlaneWorkLogRow(log: HFWorkLog, onClick: () -> Unit) {
    val status = HFWorkLogStatus.fromRaw(log.status)
    // iPad-style: green reference code on the left (like "00 00/481"),
    // title in white, status on the right.
    val refBadge = buildReferenceBadge(log)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        if (refBadge != null) {
            Text(
                refBadge,
                color = HFColors.StatusGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.size(4.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    log.title.ifBlank { "Untitled work log" },
                    color = HFColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!log.assignedUserName.isNullOrBlank()) {
                        AssignedPill(log.assignedUserName)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (log.loggedMinutes > 0) {
                        MutedPill(formatMinutesShort(log.loggedMinutes))
                    }
                }
            }
            StatusDot(color = status.color, label = status.label)
        }
    }
}

/** Build the "REF / page" badge iPad shows on each work-log row. */
private fun buildReferenceBadge(log: HFWorkLog): String? {
    val ref = log.referenceCode?.takeIf { it.isNotBlank() }
    val page = log.manualPageStart
    return when {
        ref != null && page != null -> "$ref / $page"
        ref != null -> ref
        page != null -> "p. $page"
        else -> null
    }
}

@Composable
private fun AssignedPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HFColors.StatusCyan.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = HFColors.StatusCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MutedPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMinutesShort(m: Int): String {
    val h = m / 60
    val rem = m % 60
    return when {
        h == 0 -> "${m}m"
        rem == 0 -> "${h}h"
        else -> "${h}h ${rem}m"
    }
}

// ---------- Edit + Delete sheets ----------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditPlaneSheet(plane: HFPlane, onDismiss: () -> Unit) {
    var tail by androidx.compose.runtime.mutableStateOf(plane.tailNumber)
    var display by androidx.compose.runtime.mutableStateOf(plane.displayName)
    var busy by androidx.compose.runtime.mutableStateOf(false)
    var error by androidx.compose.runtime.mutableStateOf<String?>(null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${plane.tailNumber}", color = HFColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(value = tail, onValueChange = { tail = it.uppercase() }, label = { Text("Tail Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(value = display, onValueChange = { display = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                busy = true; error = null
                scope.launch {
                    when (val r = SharedStore.updatePlane(plane.id, tail, display, plane.outlineHex ?: "#FFFFFF", plane.arrivalDate, plane.deadlineDate)) {
                        SharedStore.CreateResult.Success -> onDismiss()
                        is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                    }
                }
            }, enabled = !busy) { Text(if (busy) "Saving…" else "Save", color = HFColors.StatusGreen) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DeletePlaneSheet(plane: HFPlane, onDismiss: () -> Unit) {
    var busy by androidx.compose.runtime.mutableStateOf(false)
    var error by androidx.compose.runtime.mutableStateOf<String?>(null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete ${plane.tailNumber}?", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("This will delete this aircraft, its open work logs, and manuals. Completed work logs are preserved for billing.", color = HFColors.OnSurface, fontSize = 13.sp)
                if (error != null) { Spacer(Modifier.size(8.dp)); Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                busy = true; error = null
                scope.launch {
                    when (val r = SharedStore.deletePlaneWithHistory(plane.id, plane.tailNumber)) {
                        SharedStore.CreateResult.Success -> onDismiss()
                        is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                    }
                }
            }, enabled = !busy) { Text(if (busy) "Deleting…" else "Delete", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel") } }
    )
}
