package com.hangarflow.app.ui.worklogs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFStatusFilter
import com.hangarflow.app.data.model.HFWorkCategory
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.data.model.HFWorkLogStatus
import com.hangarflow.app.ui.theme.HFColors
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.cloud.HFCloudSyncService
import kotlinx.coroutines.launch

@Composable
fun WorkLogsTab() {
    // Work Logs | Squawks segment — admins want a "punch list" Squawks view
    // living inside the Work Logs screen so the open-items channels read
    // together. Flipping to Squawks embeds the existing SquawksHub (same
    // create / mark-done UX). Mirrors the Desktop/macOS toggle.
    var viewMode by remember { mutableStateOf("worklogs") }
    Column(modifier = Modifier.fillMaxSize()) {
        WorkLogsViewToggle(viewMode) { viewMode = it }
        if (viewMode == "squawks") {
            com.hangarflow.app.ui.hubs.SquawksHub()
        } else {
            WorkLogsContent()
        }
    }
}

@Composable
private fun WorkLogsViewToggle(viewMode: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ViewToggleChip("Work Logs", viewMode == "worklogs", HFColors.StatusGreen) { onChange("worklogs") }
        ViewToggleChip("Squawks", viewMode == "squawks", HFColors.StatusOrange) { onChange("squawks") }
    }
}

@Composable
private fun ViewToggleChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent else HFColors.OnSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) HFColors.BrandInk else HFColors.OnSurface.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WorkLogsContent() {
    val state by SharedStore.state.collectAsState()
    var statusFilter by remember { mutableStateOf(HFStatusFilter.All) }
    var categoryFilter by remember { mutableStateOf<HFWorkCategory?>(null) }
    var linkingLog by remember { mutableStateOf<HFWorkLog?>(null) }

    val filtered = remember(state.workLogs, statusFilter, categoryFilter) {
        state.workLogs.filter { log ->
            val status = HFWorkLogStatus.fromRaw(log.status)
            val category = HFWorkCategory.fromRaw(log.category)
            statusFilter.matches(status) &&
                (categoryFilter == null || categoryFilter == category)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        WorkLogsHeader(total = filtered.size, overall = state.workLogs.size)
        Spacer(Modifier.height(12.dp))
        StatusFilterRow(selected = statusFilter, onSelect = { statusFilter = it })
        Spacer(Modifier.height(10.dp))
        CategoryFilterRow(
            selected = categoryFilter,
            onSelect = { categoryFilter = it }
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            EmptyWorkLogs(
                message = if (state.workLogs.isEmpty())
                    "No work logs yet — admins import them from the Mac."
                else
                    "No work logs match the current filters."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { log ->
                    WorkLogCard(log = log, onClick = { linkingLog = log })
                }
            }
        }
    }

    linkingLog?.let { log ->
        WorkLogLinkSheet(log = log, onDismiss = { linkingLog = null })
    }
}

@Composable
private fun WorkLogsHeader(total: Int, overall: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Work Logs",
            color = HFColors.OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(10.dp))
        Text(
            if (total == overall) "$total" else "$total / $overall",
            color = HFColors.OnSurfaceMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: HFStatusFilter,
    onSelect: (HFStatusFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HFStatusFilter.entries.forEach { filter ->
            FilterPill(
                label = filter.label,
                isSelected = filter == selected,
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: HFWorkCategory?,
    onSelect: (HFWorkCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterPill(
            label = "All",
            isSelected = selected == null,
            onClick = { onSelect(null) }
        )
        HFWorkCategory.entries.forEach { cat ->
            FilterPill(
                label = cat.label,
                isSelected = selected == cat,
                onClick = { onSelect(cat) }
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) HFColors.BrandWhite else HFColors.SurfaceElevated
    val fg = if (isSelected) HFColors.BrandInk else HFColors.OnSurface
    val border = if (isSelected) HFColors.BrandWhite else HFColors.OutlineSubtle

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
private fun WorkLogCard(log: HFWorkLog, onClick: () -> Unit = {}) {
    val status = HFWorkLogStatus.fromRaw(log.status)
    val category = HFWorkCategory.fromRaw(log.category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.SurfaceElevated)
            .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.title.ifBlank { "Untitled work log" },
                    color = HFColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaPill(
                        text = log.planeTailNumber.ifBlank { "No tail" },
                        color = HFColors.OnSurfaceMuted
                    )
                    Spacer(Modifier.size(6.dp))
                    MetaPill(text = category.label, color = HFColors.OnSurfaceMuted)
                    if (!log.assignedUserName.isNullOrBlank()) {
                        Spacer(Modifier.size(6.dp))
                        MetaPill(text = log.assignedUserName, color = HFColors.StatusCyan)
                    }
                }
            }
            StatusDot(color = status.color, label = status.label)
        }

        if (log.details.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = log.details,
                color = HFColors.OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 3
            )
        }

        if (log.loggedMinutes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMinutes(log.loggedMinutes),
                color = HFColors.OnSurfaceFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyWorkLogs(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = HFColors.OnSurfaceMuted,
            fontSize = 13.sp
        )
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m logged"
        m == 0 -> "${h}h logged"
        else -> "${h}h ${m}m logged"
    }
}

/**
 * Tap-to-link sheet for D. Searches `hf_manual_references` (sections first)
 * scoped to the work log's plane, shows the matches, and persists the chosen
 * reference onto the work log. Mirrors the Desktop/macOS/iOS "possible
 * matches" flow — here the search runs server-side since Android doesn't keep
 * references in the local snapshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkLogLinkSheet(log: HFWorkLog, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val authState by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val cloud = remember { HFCloudSyncService() }
    var query by remember { mutableStateOf(log.title) }
    var results by remember { mutableStateOf(listOf<HFCloudSyncService.ManualSearchHit>()) }
    var loading by remember { mutableStateOf(false) }

    fun runSearch() {
        val orgId = authState.orgId ?: return
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); return }
        loading = true
        scope.launch {
            results = runCatching {
                cloud.searchManualReferences(orgId, q, log.planeTailNumber)
            }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(log.id) { runSearch() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Background,
        contentColor = HFColors.OnSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Link manual reference", color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                log.title.ifBlank { "Untitled work log" },
                color = HFColors.OnSurfaceMuted, fontSize = 13.sp, maxLines = 2
            )

            if (log.referenceId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HFColors.StatusGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Linked: ${log.referenceCode ?: log.referenceTitle ?: "section"}",
                        color = HFColors.StatusGreen, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Unlink",
                        color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            SharedStore.linkWorkLogToReference(log.id, null, null, null, null, null, null)
                            onDismiss()
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
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
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HFColors.StatusBlue.copy(alpha = 0.16f))
                        .clickable { runSearch() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("Search", color = HFColors.StatusBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = HFColors.OnSurface, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
                results.isEmpty() -> Text(
                    "No matches — try a different search term.",
                    color = HFColors.OnSurfaceMuted, fontSize = 13.sp
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(HFColors.SurfaceElevated)
                                .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(12.dp))
                                .clickable {
                                    SharedStore.linkWorkLogToReference(
                                        log.id, hit.id, hit.referenceCode, hit.title,
                                        hit.pageStart, hit.pageEnd, hit.sourceManualName
                                    )
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.title?.ifBlank { "(untitled section)" } ?: "(untitled section)",
                                    color = HFColors.OnSurface, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    hit.referenceCode?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = HFColors.OnSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.size(6.dp))
                                    }
                                    hit.pageStart?.let {
                                        Text("p. $it", color = HFColors.OnSurfaceFaint, fontSize = 11.sp)
                                    }
                                }
                            }
                            Text("Link", color = HFColors.StatusBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
