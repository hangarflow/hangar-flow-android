package com.hangarflow.app.ui.worklogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.ui.theme.HFColors
import com.hangarflow.app.util.HFInspectionKind

/**
 * Inspection-checklist panel surfaced inside the work-log detail when
 * the title looks like a phase / 100hr / 200hr / annual inspection (or
 * a named package).
 *
 * Pulls every `hf_manual_references` row tagged with the matching
 * `inspection_kind` scoped to the plane's attached manuals, then renders
 * each row as a checkable item. Check toggles persist into
 * `hf_work_logs.checklist_state` and broadcast to other techs.
 *
 * Self-hides when the title isn't an inspection (fromTitle == null).
 */
@Composable
fun InspectionChecklistPanel(workLog: HFWorkLog) {
    val state by SharedStore.state.collectAsState()
    val authState by AuthManager.state.collectAsState()

    val kind = remember(workLog.id, workLog.title) {
        HFInspectionKind.fromTitle(workLog.title)
    } ?: return  // Not an inspection → hide.

    val orgId = authState.orgId ?: return

    // Android scopes manuals by plane directly (no junction table in the
    // local snapshot) — match the plane by id or tail number.
    val planeManualIds = remember(state.manuals, workLog.planeId, workLog.planeTailNumber) {
        state.manuals
            .filter { m ->
                (workLog.planeId != null && m.planeId == workLog.planeId) ||
                    m.planeTailNumber?.equals(workLog.planeTailNumber, ignoreCase = true) == true
            }
            .map { it.id }
            .distinct()
    }

    var items by remember(workLog.id, kind, planeManualIds) {
        mutableStateOf<List<HFCloudSyncService.ManualSearchHit>>(emptyList())
    }
    var loading by remember(workLog.id, kind, planeManualIds) { mutableStateOf(true) }

    LaunchedEffect(workLog.id, kind, planeManualIds) {
        loading = true
        val cloud = HFCloudSyncService()
        items = runCatching {
            cloud.fetchInspectionChecklistRefs(
                orgId = orgId,
                manualIds = planeManualIds,
                inspectionKind = kind
            )
        }.getOrDefault(emptyList())
        loading = false
    }

    // Re-read the live work log so checkbox toggles reflect instantly
    // after the optimistic SharedStore update.
    val liveLog = state.workLogs.firstOrNull { it.id == workLog.id } ?: workLog
    val checkedMap: Map<String, Boolean> = remember(liveLog.checklistState) {
        (liveLog.checklistState ?: emptyList()).associate { it.refId to it.done }
    }
    val doneCount = items.count { checkedMap[it.id] == true }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.StatusGreen.copy(alpha = 0.06f))
            .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${HFInspectionKind.label(kind).uppercase()} INSPECTION CHECKLIST",
                color = HFColors.StatusGreen, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                CircularProgressIndicator(color = HFColors.StatusGreen, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
            } else {
                Text(
                    "$doneCount / ${items.size} done",
                    color = HFColors.StatusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // Progress bar.
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { doneCount.toFloat() / items.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = HFColors.StatusGreen,
                trackColor = HFColors.OnSurface.copy(alpha = 0.10f)
            )
        }

        Spacer(Modifier.height(10.dp))

        when {
            loading && items.isEmpty() -> {
                Text(
                    "Scanning ${planeManualIds.size} attached manual${if (planeManualIds.size == 1) "" else "s"} for ${HFInspectionKind.label(kind)} items…",
                    color = HFColors.OnSurfaceMuted, fontSize = 11.sp
                )
            }
            items.isEmpty() -> {
                Text(
                    "No ${HFInspectionKind.label(kind)} inspection items tagged in this plane's manuals yet. Admin can tag references on import or directly in Files.",
                    color = HFColors.OnSurfaceMuted, fontSize = 11.sp
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { ref ->
                        val isDone = checkedMap[ref.id] == true
                        // Split "Cockpit Blower - Operational Test" into a
                        // name + smaller action subtitle for a clean row.
                        val full = ref.title.orEmpty().trim()
                        val idx = full.indexOf(" - ")
                        val rowName = if (idx > 0) full.take(idx).trim() else full
                        val rowAction = if (idx > 0) full.substring(idx + 3).trim() else ""

                        // Sign-off initials from the persisted entry.
                        val entry = liveLog.checklistState?.firstOrNull { it.refId == ref.id }
                        val signOff = entry?.initials?.takeIf { it.isNotBlank() }
                            ?: entry?.by?.takeIf { it.isNotBlank() }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDone) HFColors.StatusGreen.copy(alpha = 0.08f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (isDone) HFColors.StatusGreen.copy(alpha = 0.30f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    SharedStore.setWorkLogChecklistItem(workLog.id, ref.id, !isDone)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox.
                            Box(
                                modifier = Modifier.size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDone) HFColors.StatusGreen else Color.White.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        if (isDone) HFColors.StatusGreen else Color.White.copy(alpha = 0.20f),
                                        RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Text("✓", color = HFColors.BrandInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(10.dp))

                            // Reference code badge.
                            ref.referenceCode?.takeIf { it.isNotBlank() }?.let { code ->
                                Text(
                                    code,
                                    color = HFColors.StatusGreen, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(60.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }

                            // Name + action subtitle + sign-off.
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    rowName.ifBlank { "(no title)" },
                                    color = if (isDone) HFColors.OnSurfaceMuted else HFColors.OnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                if (rowAction.isNotBlank()) {
                                    Text(rowAction, color = HFColors.OnSurfaceMuted, fontSize = 10.sp, maxLines = 1)
                                }
                                if (isDone && !signOff.isNullOrBlank()) {
                                    Text(
                                        "✓ $signOff",
                                        color = HFColors.StatusGreen, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
