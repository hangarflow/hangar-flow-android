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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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

/**
 * Source bubbles for the Work Logs list — slice items by where they
 * came from so a tech can read each channel on its own:
 *   All · Due List · Work Card · Inspections
 * Mirrors the Desktop/macOS WorkLogSource filter.
 */
private enum class WorkLogSource(val label: String) {
    ALL("All"),
    DUE_LIST("Due List"),
    WORK_CARD("Work Card"),
    INSPECTIONS("Inspections");

    fun matches(wl: HFWorkLog): Boolean = when (this) {
        ALL -> true
        INSPECTIONS -> isInspection(wl)
        DUE_LIST -> isDueList(wl)
        // Work-card items that aren't due-list rows and aren't the
        // inspection package parents — the loose tasks off the work card.
        WORK_CARD -> wl.isImportedRecord && !isDueList(wl) && !isInspection(wl)
    }

    companion object {
        private val INTERVAL_RE = Regex("""\b\d{1,4}\s*-?\s*(hr|hrs|hour|hours|fh)\b""", RegexOption.IGNORE_CASE)

        fun isInspection(wl: HFWorkLog): Boolean {
            if (com.hangarflow.app.util.HFInspectionKind.fromTitle(wl.title) != null) return true
            val t = wl.title.lowercase()
            return t.contains("inspection") || t.contains("package") || INTERVAL_RE.containsMatchIn(t)
        }

        fun isDueList(wl: HFWorkLog): Boolean {
            val hay = "${wl.importSourceName ?: ""} ${wl.details} ${wl.title}".lowercase()
            return hay.contains("due list") || hay.contains("due items") || hay.contains("due-list")
        }
    }
}

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
    // iOS-style segmented control: a single rounded track holding two
    // equal-width segments; the active one rides on a solid white pill.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ViewToggleChip("Work Logs", viewMode == "worklogs", Modifier.weight(1f)) { onChange("worklogs") }
        ViewToggleChip("Squawks", viewMode == "squawks", Modifier.weight(1f)) { onChange("squawks") }
    }
}

@Composable
private fun ViewToggleChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) HFColors.BrandWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) HFColors.BrandInk else HFColors.OnSurface.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WorkLogsContent() {
    val state by SharedStore.state.collectAsState()
    var statusFilter by remember { mutableStateOf(HFStatusFilter.All) }
    var categoryFilter by remember { mutableStateOf<HFWorkCategory?>(null) }
    var sourceFilter by remember { mutableStateOf(WorkLogSource.ALL) }
    var linkingLog by remember { mutableStateOf<HFWorkLog?>(null) }

    // Everything except the source bubble — feeds both the bubble counts
    // and (after the source predicate) the final list, so each bubble can
    // preview how many it WOULD show.
    val preSource = remember(state.workLogs, statusFilter, categoryFilter) {
        state.workLogs.filter { log ->
            val status = HFWorkLogStatus.fromRaw(log.status)
            val category = HFWorkCategory.fromRaw(log.category)
            statusFilter.matches(status) &&
                (categoryFilter == null || categoryFilter == category)
        }
    }

    val filtered = remember(preSource, sourceFilter) {
        preSource.filter { log -> sourceFilter.matches(log) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        WorkLogsHeader(total = filtered.size, overall = state.workLogs.size)
        Spacer(Modifier.height(14.dp))

        FilterSectionLabel("Source")
        Spacer(Modifier.height(6.dp))
        SourceFilterRow(
            selected = sourceFilter,
            counts = WorkLogSource.entries.associateWith { src ->
                if (src == WorkLogSource.ALL) preSource.size else preSource.count { src.matches(it) }
            },
            onSelect = { sourceFilter = it }
        )
        Spacer(Modifier.height(12.dp))

        FilterSectionLabel("Status")
        Spacer(Modifier.height(6.dp))
        StatusFilterRow(selected = statusFilter, onSelect = { statusFilter = it })
        Spacer(Modifier.height(12.dp))

        FilterSectionLabel("Category")
        Spacer(Modifier.height(6.dp))
        CategoryFilterRow(
            selected = categoryFilter,
            onSelect = { categoryFilter = it }
        )
        Spacer(Modifier.height(14.dp))

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
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (total == overall) "$total" else "$total / $overall",
            color = HFColors.OnSurface.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** iOS-style uppercase section caption above each filter row. */
@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = HFColors.OnSurface.copy(alpha = 0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp
    )
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
private fun SourceFilterRow(
    selected: WorkLogSource,
    counts: Map<WorkLogSource, Int>,
    onSelect: (WorkLogSource) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkLogSource.entries.forEach { src ->
            FilterPill(
                label = "${src.label} · ${counts[src] ?: 0}",
                isSelected = src == selected,
                onClick = { onSelect(src) }
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
    val bg = if (isSelected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.06f)
    val fg = if (isSelected) HFColors.BrandInk else HFColors.OnSurface
    val border = if (isSelected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.10f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
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
    val isPinned = !log.pinnedAt.isNullOrBlank()

    // iOS work-log card anatomy:
    //   meta row → ATA/ref pill · solid status pill · spacer · pin · updated date
    //   title    → 16sp semibold white
    //   bottom   → green page badge · neutral badges · spacer · chevron
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPinned) HFColors.StatusOrange.copy(alpha = 0.08f)
                else HFColors.OnSurface.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (isPinned) HFColors.StatusOrange.copy(alpha = 0.35f)
                else HFColors.OnSurface.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Meta row
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ata = (log.sourceAtaCode?.takeIf { it.isNotBlank() }
                ?: log.referenceCode?.takeIf { it.isNotBlank() })
            if (ata != null) {
                NeutralPill(text = ata)
                Spacer(Modifier.size(8.dp))
            }
            SolidStatusPill(color = status.color, label = status.label)
            Spacer(Modifier.weight(1f))
            // Pin / unpin toggle — pinned logs float to the top of the list.
            // Mirrors the Desktop pin affordance (any org member can pin).
            PinToggle(isPinned = isPinned) {
                SharedStore.setWorkLogPinned(log.id, !isPinned)
            }
            val updated = formatUpdatedDate(log.updatedAt)
            if (updated != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    updated,
                    color = HFColors.OnSurface.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Title
        Text(
            text = log.title.ifBlank { "Untitled work log" },
            color = HFColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3
        )

        if (log.details.isNotBlank()) {
            Text(
                text = log.details,
                color = HFColors.OnSurface.copy(alpha = 0.68f),
                fontSize = 12.sp,
                maxLines = 3
            )
        }

        // Bottom row — badges + chevron
        Row(verticalAlignment = Alignment.CenterVertically) {
            log.manualPageStart?.takeIf { it > 0 }?.let { page ->
                GreenPageBadge(page = page)
                Spacer(Modifier.size(8.dp))
            }
            NeutralBadge(text = log.planeTailNumber.ifBlank { "No tail" })
            Spacer(Modifier.size(8.dp))
            NeutralBadge(text = category.label)
            if (!log.assignedUserName.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                CyanBadge(text = log.assignedUserName)
            }
            if (log.loggedMinutes > 0) {
                Spacer(Modifier.size(8.dp))
                NeutralBadge(text = formatMinutes(log.loggedMinutes))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "›",
                color = HFColors.OnSurface.copy(alpha = 0.36f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Pin / unpin toggle shown in the work-log card meta row. Pinned →
 *  solid orange capsule; unpinned → subtle outline. Has its own
 *  clickable so tapping it doesn't open the card's link sheet. */
@Composable
private fun PinToggle(isPinned: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(
                if (isPinned) HFColors.StatusOrange.copy(alpha = 0.18f)
                else HFColors.OnSurface.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (isPinned) HFColors.StatusOrange.copy(alpha = 0.45f)
                else HFColors.OnSurface.copy(alpha = 0.12f),
                RoundedCornerShape(100.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (isPinned) "📌 Pinned" else "📌 Pin",
            color = if (isPinned) HFColors.StatusOrange else HFColors.OnSurface.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Capsule pill, white@8% fill, white text — the iOS ATA/ref code pill. */
@Composable
private fun NeutralPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = HFColors.OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Solid status-colored capsule with black text — the iOS status pill. */
@Composable
private fun SolidStatusPill(color: Color, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HFColors.BrandInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Green page-jump badge — book glyph + monospaced page number. */
@Composable
private fun GreenPageBadge(page: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.StatusGreen.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("📖 p. $page", color = HFColors.StatusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Neutral capsule badge — white@8% fill, muted white text. */
@Composable
private fun NeutralBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = HFColors.OnSurface.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Cyan accent badge — used for the assigned-tech name. */
@Composable
private fun CyanBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.StatusCyan.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = HFColors.StatusCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Abbreviated "MMM d" from the ISO updated_at, mirroring the iOS
 *  right-aligned date stamp. Returns null when there's no usable date. */
private fun formatUpdatedDate(iso: String?): String? {
    val raw = iso?.takeIf { it.isNotBlank() } ?: return null
    // Expect "YYYY-MM-DD..." — pull month/day without pulling in a parser.
    val datePart = raw.take(10)
    val bits = datePart.split("-")
    if (bits.size < 3) return null
    val month = bits[1].toIntOrNull() ?: return null
    val day = bits[2].toIntOrNull() ?: return null
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    if (month < 1 || month > 12) return null
    return "${names[month - 1]} $day"
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

            // Inspection-checklist panel — surfaces tagged checklist items
            // (e.g. 200hr inspection) for the plane's attached manuals.
            // Self-hides if the title isn't an inspection or no tagged refs
            // exist for the plane.
            InspectionChecklistPanel(workLog = log)

            WorkLogAIOrganizeCard(log = log)

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

/** AI-organize card: AI-recommended parts + an "Organize with AI" button
 *  (admins / lead techs). The primary manual reference the AI picks is set
 *  on the work log itself and shown in the linked row above. */
@Composable
private fun WorkLogAIOrganizeCard(log: HFWorkLog) {
    val authState by AuthManager.state.collectAsState()
    val aiEnabled by SharedStore.aiIndexingEnabled.collectAsState()
    val organizing by SharedStore.isOrganizing.collectAsState()
    val canOrganize = authState.isAdmin || authState.isLeadTech
    val enriched = log.aiEnrichedAt != null
    if (!(aiEnabled || enriched || canOrganize)) return

    // Re-read the live log so parts/enriched update after an organize pass.
    val liveLog by remember(log.id) {
        derivedStateOf { SharedStore.state.value.workLogs.firstOrNull { it.id == log.id } ?: log }
    }
    val parts = liveLog.aiRecommendedParts ?: emptyList()
    val scope = rememberCoroutineScope()
    var status by remember(log.id) { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.StatusBlue.copy(alpha = 0.06f))
            .androidx_border()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✨ AI ASSIST", color = HFColors.StatusBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            if (canOrganize) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(100.dp))
                        .background(HFColors.StatusBlue.copy(alpha = 0.15f))
                        .clickable(enabled = !organizing) {
                            scope.launch {
                                val n = SharedStore.organizeWorkLog(log.id)
                                status = if (n > 0) "Updated by AI." else "No new matches found."
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (organizing) "Working…" else if (enriched) "Re-run AI" else "Organize with AI",
                        color = HFColors.StatusBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (parts.isNotEmpty()) {
            Text("RECOMMENDED PARTS", color = HFColors.OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            parts.forEach { p ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    Text(
                        if (!p.partNumber.isNullOrBlank()) p.partNumber!! else p.description,
                        color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (!p.partNumber.isNullOrBlank() && p.description.isNotBlank()) {
                        Text(p.description, color = HFColors.OnSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    if (p.reason.isNotBlank()) {
                        Text(p.reason, color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }
        } else {
            Text(
                if (enriched) "AI found no specific parts for this log."
                else "Let AI link this log to the right manual reference and suggest parts.",
                color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 12.sp
            )
        }
        if (status.isNotBlank()) {
            Text(status, color = HFColors.StatusBlue.copy(alpha = 0.85f), fontSize = 11.sp)
        }
    }
}

/** Subtle blue hairline border matching the AI card accent. */
private fun Modifier.androidx_border(): Modifier =
    this.border(1.dp, HFColors.StatusBlue.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
