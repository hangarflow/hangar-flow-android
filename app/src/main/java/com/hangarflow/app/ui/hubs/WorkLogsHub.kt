package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.data.model.HFWorkCategory
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.data.model.HFWorkLogStatus
import com.hangarflow.app.ui.theme.HFColors

/**
 * Work Logs hub sheet. Matches the iOS layout: toggle at top for
 * "assigned to me", scrollable status chips, scrollable category chips,
 * then a list of work log cards. Tapping a card's status pill opens a
 * bottom-sheet status picker that writes through to Supabase.
 */
/**
 * Top-level Work Logs section router. Two entry cards mirror the iPad:
 *   • Plane Work Logs → pick a plane, then see its categories.
 *   • Assigned Work Orders → only the current user's assigned logs,
 *     grouped by plane for a clean view.
 * Admin "+" FAB is visible on both flows.
 */
private enum class WorkLogsSection { Menu, PlaneLogs, Assigned }

@Composable
fun WorkLogsHub(onOpenWorkLog: (HFWorkLog) -> Unit) {
    var section by remember { mutableStateOf(WorkLogsSection.Menu) }
    var selectedPlaneId by remember { mutableStateOf<String?>(null) }

    com.hangarflow.app.ui.admin.AdminFabOverlay(
        mode = com.hangarflow.app.ui.admin.AdminCreateMode.WorkLog
    ) {
        HFPullToRefreshHost {
            when (section) {
                WorkLogsSection.Menu -> WorkLogsMenu(
                    onOpenPlaneLogs = {
                        selectedPlaneId = null
                        section = WorkLogsSection.PlaneLogs
                    },
                    onOpenAssigned = {
                        selectedPlaneId = null
                        section = WorkLogsSection.Assigned
                    }
                )
                WorkLogsSection.PlaneLogs -> PlaneLogsFlow(
                    selectedPlaneId = selectedPlaneId,
                    onSelectPlane = { selectedPlaneId = it },
                    onBackToMenu = { section = WorkLogsSection.Menu },
                    onOpenWorkLog = onOpenWorkLog
                )
                WorkLogsSection.Assigned -> AssignedFlow(
                    selectedPlaneId = selectedPlaneId,
                    onSelectPlane = { selectedPlaneId = it },
                    onBackToMenu = { section = WorkLogsSection.Menu },
                    onOpenWorkLog = onOpenWorkLog
                )
            }
        }
    }
}

// ---------- Top-level menu (two cards) ----------

@Composable
private fun WorkLogsMenu(
    onOpenPlaneLogs: () -> Unit,
    onOpenAssigned: () -> Unit
) {
    val state by SharedStore.state.collectAsState()
    val me = state.currentUser
    val myAssigned = remember(state.workLogs, me) {
        state.workLogs.count { log ->
            (me != null && log.assignedUserId == me.id) ||
                (me != null && log.assignedUserName?.equals(me.displayName, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        WorkLogsMenuCard(
            title = "Plane Work Logs",
            subtitle = "Browse every plane. Tap one to see its categories and open work.",
            accent = HFColors.StatusBlue,
            statText = "${state.planes.size} planes",
            onClick = onOpenPlaneLogs
        )
        WorkLogsMenuCard(
            title = "Assigned Work Orders",
            subtitle = "Only the work orders assigned to you, grouped by plane.",
            accent = HFColors.StatusCyan,
            statText = if (myAssigned == 1) "1 assigned" else "$myAssigned assigned",
            onClick = onOpenAssigned
        )
    }
}

@Composable
private fun WorkLogsMenuCard(
    title: String,
    subtitle: String,
    accent: Color,
    statText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            color = HFColors.OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = HFColors.OnSurface.copy(alpha = 0.70f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(accent.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                statText,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ---------- Flow: Plane Work Logs ----------

@Composable
private fun PlaneLogsFlow(
    selectedPlaneId: String?,
    onSelectPlane: (String?) -> Unit,
    onBackToMenu: () -> Unit,
    onOpenWorkLog: (HFWorkLog) -> Unit
) {
    val state by SharedStore.state.collectAsState()
    val plane = state.planes.firstOrNull { it.id == selectedPlaneId }
    if (plane != null) {
        PlaneDetailView(
            plane = plane,
            onBack = { onSelectPlane(null) },
            onOpenWorkLog = onOpenWorkLog
        )
        return
    }
    PlaneGridForWorkLogs(
        planes = state.planes,
        workLogs = state.workLogs,
        headerTitle = "Plane Work Logs",
        onBack = onBackToMenu,
        onSelectPlane = onSelectPlane
    )
}

// ---------- Flow: Assigned Work Orders ----------

@Composable
private fun AssignedFlow(
    selectedPlaneId: String?,
    onSelectPlane: (String?) -> Unit,
    onBackToMenu: () -> Unit,
    onOpenWorkLog: (HFWorkLog) -> Unit
) {
    val state by SharedStore.state.collectAsState()
    val me = state.currentUser

    val myLogs = remember(state.workLogs, me) {
        state.workLogs.filter { log ->
            (me != null && log.assignedUserId == me.id) ||
                (me != null && log.assignedUserName?.equals(me.displayName, ignoreCase = true) == true)
        }
    }
    val planesWithMine = remember(myLogs, state.planes) {
        val ids = myLogs.mapNotNull { it.planeId }.toSet()
        state.planes.filter { it.id in ids }
    }

    if (selectedPlaneId != null) {
        val plane = state.planes.firstOrNull { it.id == selectedPlaneId }
        val planeLogs = myLogs.filter { it.planeId == selectedPlaneId }
        AssignedPlaneLogList(
            plane = plane,
            logs = planeLogs,
            onBack = { onSelectPlane(null) },
            onOpenWorkLog = onOpenWorkLog
        )
        return
    }

    PlaneGridForAssigned(
        planes = planesWithMine,
        myLogs = myLogs,
        onBack = onBackToMenu,
        onSelectPlane = onSelectPlane
    )
}

// ---------- Plane grids ----------

@Composable
private fun PlaneGridForWorkLogs(
    planes: List<com.hangarflow.app.data.model.HFPlane>,
    workLogs: List<HFWorkLog>,
    headerTitle: String,
    onBack: () -> Unit,
    onSelectPlane: (String) -> Unit
) {
    val counts = remember(workLogs) {
        workLogs.groupingBy { it.planeId ?: "" }.eachCount()
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PlaneFlowHeader(title = headerTitle, onBack = onBack)
        }
        if (planes.isEmpty()) {
            item {
                IOSPlaceholderPanel(
                    message = "No planes in the shop yet. An admin can add one from the desktop or the + button."
                )
            }
        } else {
            items(planes.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { p ->
                        WorkLogPlaneCard(
                            tail = p.tailNumber,
                            name = p.displayName,
                            openCount = counts[p.id] ?: 0,
                            onClick = { onSelectPlane(p.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun PlaneGridForAssigned(
    planes: List<com.hangarflow.app.data.model.HFPlane>,
    myLogs: List<HFWorkLog>,
    onBack: () -> Unit,
    onSelectPlane: (String) -> Unit
) {
    val counts = remember(myLogs) { myLogs.groupingBy { it.planeId ?: "" }.eachCount() }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PlaneFlowHeader(title = "Assigned Work Orders", onBack = onBack)
        }
        if (planes.isEmpty()) {
            item {
                IOSPlaceholderPanel(
                    message = "Nothing assigned to you yet. When an admin assigns work, the planes will show up here."
                )
            }
        } else {
            items(planes.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { p ->
                        WorkLogPlaneCard(
                            tail = p.tailNumber,
                            name = p.displayName,
                            openCount = counts[p.id] ?: 0,
                            onClick = { onSelectPlane(p.id) },
                            accent = HFColors.StatusCyan,
                            openCountSuffix = "assigned",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun PlaneFlowHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(HFColors.OnSurface.copy(alpha = 0.10f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = HFColors.OnSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WorkLogPlaneCard(
    tail: String,
    name: String,
    openCount: Int,
    onClick: () -> Unit,
    accent: Color = HFColors.StatusBlue,
    openCountSuffix: String = "open",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(tail, color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (name.isNotBlank() && name != tail) {
            Text(
                name,
                color = HFColors.OnSurface.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (openCount == 1) "1 $openCountSuffix" else "$openCount $openCountSuffix",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ---------- Assigned plane detail (clean list) ----------

@Composable
private fun AssignedPlaneLogList(
    plane: com.hangarflow.app.data.model.HFPlane?,
    logs: List<HFWorkLog>,
    onBack: () -> Unit,
    onOpenWorkLog: (HFWorkLog) -> Unit
) {
    val sorted = remember(logs) {
        logs.sortedWith(
            compareBy(
                { HFWorkLogStatus.fromRaw(it.status) == HFWorkLogStatus.Done },
                { it.updatedAt ?: "" }
            )
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaneFlowHeader(
                    title = plane?.tailNumber ?: "Plane",
                    onBack = onBack
                )
                if (plane != null && plane.displayName.isNotBlank() && plane.displayName != plane.tailNumber) {
                    Text(
                        plane.displayName,
                        color = HFColors.OnSurface.copy(alpha = 0.60f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    if (sorted.size == 1) "1 work order assigned to you"
                    else "${sorted.size} work orders assigned to you",
                    color = HFColors.StatusCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
        if (sorted.isEmpty()) {
            item {
                IOSPlaceholderPanel(message = "No assigned work orders for this plane.")
            }
        } else {
            items(sorted, key = { it.id }) { log ->
                AssignedWorkLogRow(log = log, onClick = { onOpenWorkLog(log) })
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun AssignedWorkLogRow(log: HFWorkLog, onClick: () -> Unit) {
    val status = HFWorkLogStatus.fromRaw(log.status)
    val category = HFWorkCategory.fromRaw(log.category)
    val refBadge = buildIPadReferenceBadge(log)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.StatusCyan.copy(alpha = 0.06f))
            .border(1.dp, HFColors.StatusCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
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
            Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HFColors.OnSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            category.label,
                            color = HFColors.OnSurface.copy(alpha = 0.70f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Paper trail — who logged (or imported) this work.
                Spacer(Modifier.height(3.dp))
                Text(
                    if (log.isImportedRecord)
                        "Imported by ${log.importSourceName?.takeIf { it.isNotBlank() } ?: "Imported"}"
                    else
                        "Added by ${log.createdByUserName?.takeIf { it.isNotBlank() } ?: "—"}",
                    color = HFColors.OnSurface.copy(alpha = 0.50f),
                    fontSize = 10.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(status.color))
                Spacer(Modifier.width(6.dp))
                Text(status.label, color = status.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkLogsHubContent(onOpenWorkLog: (HFWorkLog) -> Unit) {
    val state by SharedStore.state.collectAsState()
    val authState by com.hangarflow.app.auth.AuthManager.state.collectAsState()
    var statusFilter by remember { mutableStateOf(HFStatusFilter.All) }
    var categoryFilter by remember { mutableStateOf<HFWorkCategory?>(null) }
    var assignedToMe by remember { mutableStateOf(false) }
    var selectedPlaneId by remember { mutableStateOf<String?>(null) }
    var statusSheetForLog by remember { mutableStateOf<HFWorkLog?>(null) }
    // Admin-only "show work assigned to <tech>" filter. `null` = everyone,
    // `"__unassigned__"` = only open-to-anyone logs. Tech UI doesn't expose
    // this — techs stick with the "Assigned to me" toggle below.
    var assignedUserFilter by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val me = state.currentUser
    val myUserId = me?.id
    val myName = me?.displayName
    val isAdmin = authState.isAdmin

    val planes = remember(state.planes) { state.planes.sortedBy { it.tailNumber.lowercase() } }
    val planeWorkLogCounts = remember(state.workLogs) {
        state.workLogs.groupingBy { it.planeId ?: "" }.eachCount()
    }

    val filtered = remember(
        state.workLogs, statusFilter, categoryFilter, assignedToMe,
        myUserId, selectedPlaneId, assignedUserFilter, state.users
    ) {
        val filterUser = state.users.firstOrNull { it.id == assignedUserFilter }
        state.workLogs.filter { log ->
            val status = HFWorkLogStatus.fromRaw(log.status)
            val category = HFWorkCategory.fromRaw(log.category)
            val mine = (myUserId != null && log.assignedUserId == myUserId) ||
                (myName != null && log.assignedUserName?.equals(myName, ignoreCase = true) == true)
            val planeOk = selectedPlaneId == null || log.planeId == selectedPlaneId
            // Admin assignee filter. Match on id OR name so legacy rows
            // (assigned_user_id missing, only name populated) still filter.
            val assigneeOk = when (assignedUserFilter) {
                null -> true
                "__unassigned__" -> log.assignedUserId.isNullOrBlank() && log.assignedUserName.isNullOrBlank()
                else -> log.assignedUserId == assignedUserFilter ||
                    (filterUser != null && filterUser.displayName.isNotBlank() &&
                        log.assignedUserName?.equals(filterUser.displayName, ignoreCase = true) == true)
            }
            planeOk &&
                assigneeOk &&
                statusFilter.matches(status) &&
                (categoryFilter == null || categoryFilter == category) &&
                (!assignedToMe || mine)
        }
    }

    val myAssignedCount = remember(state.workLogs, myUserId, myName) {
        state.workLogs.count { log ->
            (myUserId != null && log.assignedUserId == myUserId) ||
                (myName != null && log.assignedUserName?.equals(myName, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Plane picker — mirrors the Mac sidebar's plane list. Primary
        // filter for what the tech wants to see. "All" is the default.
        FilterSectionLabel("Plane")
        Spacer(Modifier.height(6.dp))
        IOSPlaneChipRow(
            planes = planes,
            selectedPlaneId = selectedPlaneId,
            workLogCountByPlane = planeWorkLogCounts,
            onSelect = { selectedPlaneId = it }
        )

        Spacer(Modifier.height(12.dp))

        if (isAdmin && state.users.isNotEmpty()) {
            FilterSectionLabel("Assigned to")
            Spacer(Modifier.height(6.dp))
            AssigneeChipRow(
                users = state.users.sortedBy { it.displayName.lowercase() },
                currentUserId = myUserId,
                workLogs = state.workLogs,
                selected = assignedUserFilter,
                onSelect = { assignedUserFilter = it }
            )
            Spacer(Modifier.height(10.dp))
        } else if (me != null) {
            IOSAssignedToMePanel(
                isActive = assignedToMe,
                count = myAssignedCount,
                userName = me.displayName,
                onToggle = { assignedToMe = !assignedToMe }
            )
            Spacer(Modifier.height(10.dp))
        }

        FilterSectionLabel("Status")
        Spacer(Modifier.height(6.dp))
        IOSStatusChipRow(selected = statusFilter, onSelect = { statusFilter = it })

        Spacer(Modifier.height(10.dp))

        FilterSectionLabel("Category")
        Spacer(Modifier.height(6.dp))
        IOSCategoryChipRow(selected = categoryFilter, onSelect = { categoryFilter = it })

        Spacer(Modifier.height(14.dp))

        // Header — includes the selected plane as a pill so it's always
        // obvious what's being shown without scrolling back to the picker.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Work Logs",
                color = HFColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (selectedPlaneId != null) {
                val plane = planes.firstOrNull { it.id == selectedPlaneId }
                if (plane != null) {
                    Spacer(Modifier.width(8.dp))
                    SelectedPlaneTag(tail = plane.tailNumber)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${filtered.size}",
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            IOSPlaceholderPanel(
                message = when {
                    state.workLogs.isEmpty() -> "No work logs yet — admins import them from the desktop."
                    selectedPlaneId != null -> "No work logs on this plane match the filters."
                    else -> "No work logs match the current filters."
                }
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { log ->
                    val mine = (myUserId != null && log.assignedUserId == myUserId) ||
                        (myName != null && log.assignedUserName?.equals(myName, ignoreCase = true) == true)
                    IOSWorkLogCard(
                        log = log,
                        isAssignedToMe = mine,
                        onTapStatus = { statusSheetForLog = log },
                        onTapCard = { onOpenWorkLog(log) }
                    )
                }
            }
        }
    }

    statusSheetForLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { statusSheetForLog = null },
            sheetState = sheetState,
            containerColor = HFColors.Surface,
            contentColor = HFColors.OnSurface
        ) {
            Column {
                IOSStatusPickerSheet(
                    currentStatus = HFWorkLogStatus.fromRaw(log.status),
                    onPick = { newStatus ->
                        SharedStore.updateWorkLogStatus(log.id, newStatus.raw)
                        statusSheetForLog = null
                    }
                )
                // Admins get an "Assign to" picker below the status grid
                // so reassignment is one tap away from status changes.
                if (isAdmin && state.users.isNotEmpty()) {
                    AssigneePickerSection(
                        users = state.users.sortedBy { it.displayName.lowercase() },
                        currentAssigneeId = log.assignedUserId,
                        onPick = { user ->
                            SharedStore.updateWorkLogAssignee(
                                workLogId = log.id,
                                assignedUserId = user?.id,
                                assignedUserName = user?.displayName
                            )
                            statusSheetForLog = null
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ---------- section label + plane picker ----------

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
private fun SelectedPlaneTag(tail: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HFColors.BrandWhite)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            tail,
            color = HFColors.BrandInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IOSPlaneChipRow(
    planes: List<com.hangarflow.app.data.model.HFPlane>,
    selectedPlaneId: String?,
    workLogCountByPlane: Map<String, Int>,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlaneChip(
            tail = "All planes",
            accent = HFColors.OnSurface,
            count = null,
            isSelected = selectedPlaneId == null,
            onClick = { onSelect(null) }
        )
        planes.forEach { plane ->
            val outline = runCatching {
                plane.outlineHex
                    ?.removePrefix("#")
                    ?.takeIf { it.length == 6 }
                    ?.let { Color("FF$it".toLong(16)) }
            }.getOrNull() ?: HFColors.OnSurface
            PlaneChip(
                tail = plane.tailNumber,
                accent = outline,
                count = workLogCountByPlane[plane.id],
                isSelected = selectedPlaneId == plane.id,
                onClick = {
                    onSelect(if (selectedPlaneId == plane.id) null else plane.id)
                }
            )
        }
    }
}

@Composable
private fun PlaneChip(
    tail: String,
    accent: Color,
    count: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) accent.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.06f)
    val border = if (isSelected) accent else HFColors.OnSurface.copy(alpha = 0.10f)
    val fg = if (isSelected) accent else HFColors.OnSurface

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            tail,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "$count",
                color = fg.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------- assigned-to-me toggle ----------

@Composable
private fun IOSAssignedToMePanel(
    isActive: Boolean,
    count: Int,
    userName: String,
    onToggle: () -> Unit
) {
    val accent = HFColors.StatusCyan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) accent.copy(alpha = 0.14f) else HFColors.OnSurface.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (isActive) accent.copy(alpha = 0.55f) else HFColors.OnSurface.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isActive) "My Work" else "Show only my work",
                color = if (isActive) accent else HFColors.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isActive) "Filtered to $userName — tap to see all" else "$count assigned to $userName",
                color = HFColors.OnSurface.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(HFColors.OnSurface.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$count",
                color = HFColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------- filter chips ----------

/**
 * Admin-only chip row: "All" + "Unassigned" + each tech. Each tech chip
 * shows their assigned-count so an admin can see workload at a glance.
 */
@Composable
private fun AssigneeChipRow(
    users: List<com.hangarflow.app.data.model.HFUserProfile>,
    currentUserId: String?,
    workLogs: List<HFWorkLog>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val countByUserId = remember(workLogs) {
        workLogs.groupingBy { it.assignedUserId ?: "" }.eachCount()
    }
    val countByName = remember(workLogs) {
        workLogs.filter { it.assignedUserId.isNullOrBlank() && !it.assignedUserName.isNullOrBlank() }
            .groupingBy { it.assignedUserName!!.lowercase() }.eachCount()
    }
    val unassignedCount = remember(workLogs) {
        workLogs.count { it.assignedUserId.isNullOrBlank() && it.assignedUserName.isNullOrBlank() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IOSFilterChip(
            label = "All · ${workLogs.size}",
            isSelected = selected == null,
            onClick = { onSelect(null) }
        )
        IOSFilterChip(
            label = "Unassigned · $unassignedCount",
            isSelected = selected == "__unassigned__",
            onClick = { onSelect("__unassigned__") }
        )
        users.forEach { u ->
            val idCount = countByUserId[u.id] ?: 0
            val nameCount = countByName[u.displayName.lowercase()] ?: 0
            val total = idCount + nameCount
            val base = if (u.id == currentUserId) "${u.displayName.ifBlank { "You" }} (you)"
                else u.displayName.ifBlank { "Tech" }
            IOSFilterChip(
                label = "$base · $total",
                isSelected = selected == u.id,
                onClick = { onSelect(u.id) }
            )
        }
    }
}

@Composable
private fun IOSStatusChipRow(selected: HFStatusFilter, onSelect: (HFStatusFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HFStatusFilter.entries.forEach { filter ->
            IOSFilterChip(
                label = filter.label,
                isSelected = filter == selected,
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun IOSCategoryChipRow(
    selected: HFWorkCategory?,
    onSelect: (HFWorkCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IOSFilterChip(label = "All", isSelected = selected == null, onClick = { onSelect(null) })
        HFWorkCategory.entries.forEach { cat ->
            IOSFilterChip(
                label = cat.label,
                isSelected = selected == cat,
                onClick = { onSelect(cat) }
            )
        }
    }
}

@Composable
private fun IOSFilterChip(
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
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- work log card ----------

@Composable
private fun IOSWorkLogCard(
    log: HFWorkLog,
    isAssignedToMe: Boolean,
    onTapStatus: () -> Unit,
    onTapCard: () -> Unit
) {
    val status = HFWorkLogStatus.fromRaw(log.status)
    val category = HFWorkCategory.fromRaw(log.category)
    val accent = if (isAssignedToMe) HFColors.StatusCyan else HFColors.OnSurface.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isAssignedToMe) HFColors.StatusCyan.copy(alpha = 0.08f)
                else HFColors.OnSurface.copy(alpha = 0.04f)
            )
            .border(1.dp, accent, RoundedCornerShape(18.dp))
            .clickable(onClick = onTapCard)
            .padding(14.dp)
    ) {
        // Green reference/page badge — mirrors the iPad "00 00/481" tag
        // so techs can spot the manual chapter without opening the card.
        val refBadge = buildIPadReferenceBadge(log)
        if (refBadge != null) {
            Text(
                refBadge,
                color = HFColors.StatusGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.title.ifBlank { "Untitled work log" },
                    color = HFColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IOSMetaPill(
                        text = log.planeTailNumber.ifBlank { "No tail" },
                        color = HFColors.OnSurface.copy(alpha = 0.55f)
                    )
                    Spacer(Modifier.width(6.dp))
                    IOSMetaPill(text = category.label, color = HFColors.OnSurface.copy(alpha = 0.55f))
                    if (!log.assignedUserName.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        IOSMetaPill(text = log.assignedUserName, color = HFColors.StatusCyan)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTapStatus)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IOSStatusDot(color = status.color, label = status.label)
            }
        }

        if (log.details.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = log.details,
                color = HFColors.OnSurface.copy(alpha = 0.68f),
                fontSize = 12.sp,
                maxLines = 3
            )
        }

        if (log.loggedMinutes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatLoggedMinutes(log.loggedMinutes),
                color = HFColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun IOSMetaPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildIPadReferenceBadge(log: HFWorkLog): String? {
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
private fun IOSStatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- assignee picker section (admin-only, inline in status sheet) ----------

@Composable
private fun AssigneePickerSection(
    users: List<com.hangarflow.app.data.model.HFUserProfile>,
    currentAssigneeId: String?,
    onPick: (com.hangarflow.app.data.model.HFUserProfile?) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Spacer(Modifier.height(14.dp))
        Text(
            "Assign To".uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(10.dp))
        // "Unassigned" chip first so clearing is obvious.
        AssigneeRow(
            label = "Unassigned",
            subtitle = "Open to anyone",
            selected = currentAssigneeId.isNullOrBlank(),
            onClick = { onPick(null) }
        )
        Spacer(Modifier.height(8.dp))
        users.forEach { u ->
            AssigneeRow(
                label = u.displayName.ifBlank { "Tech" },
                subtitle = u.role.replaceFirstChar { it.uppercase() },
                selected = u.id == currentAssigneeId,
                onClick = { onPick(u) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AssigneeRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) HFColors.StatusCyan.copy(alpha = 0.14f) else HFColors.OnSurface.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (selected) HFColors.StatusCyan.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.10f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) HFColors.StatusCyan else HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (selected) {
            Text("✓", color = HFColors.StatusCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- status picker sheet ----------

@Composable
private fun IOSStatusPickerSheet(
    currentStatus: HFWorkLogStatus,
    onPick: (HFWorkLogStatus) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            "Change Status".uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(12.dp))
        HFWorkLogStatus.entries.forEach { status ->
            val isCurrent = status == currentStatus
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) status.color.copy(alpha = 0.14f) else HFColors.OnSurface.copy(alpha = 0.04f))
                    .border(
                        1.dp,
                        if (isCurrent) status.color.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onPick(status) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(status.color))
                Spacer(Modifier.size(10.dp))
                Text(
                    status.label,
                    color = if (isCurrent) status.color else HFColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatLoggedMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m logged"
        m == 0 -> "${h}h logged"
        else -> "${h}h ${m}m logged"
    }
}
