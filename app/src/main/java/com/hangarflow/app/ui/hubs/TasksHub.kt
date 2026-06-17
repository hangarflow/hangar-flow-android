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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFTask
import com.hangarflow.app.data.model.HFWorkCategory
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * Tasks hub for the Android tablet (tech) view.
 *
 * Mirrors the iOS `IOSTaskProgressHubView` two-section pattern:
 *  1. **Assigned Tasks** — list of tasks the tech can see, with a status
 *     update grid (Open / In Progress / Waiting on Parts / Done) when tapped.
 *  2. **Plane Progress** — one card per plane summarising open / in-progress /
 *     waiting / done counts.
 *
 * Visual tokens (card background, border, chip pills, status accents) match
 * iOS so usability studies stay consistent across platforms.
 */
@Composable
fun TasksHub() {
    HFPullToRefreshHost {
        TasksHubContent()
    }
}

@Composable
private fun TasksHubContent() {
    val state by SharedStore.state.collectAsState()
    val auth by AuthManager.state.collectAsState()
    val me = state.currentUser

    var section by remember { mutableStateOf(TaskSection.Assigned) }
    var planeFilter by remember { mutableStateOf("") } // "" == All Planes
    var assignedToMe by remember { mutableStateOf(false) }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<HFTask?>(null) }

    if (showCreateSheet) {
        TaskEditSheet(existing = null, onDismiss = { showCreateSheet = false })
    }
    editingTask?.let { t ->
        TaskEditSheet(existing = t, onDismiss = { editingTask = null })
    }

    val tasks = remember(state.tasks) {
        state.tasks.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
    }

    val filteredTasks = remember(tasks, planeFilter, assignedToMe, me?.id) {
        var list = tasks
        if (planeFilter.isNotBlank()) {
            val tail = planeFilter.uppercase()
            list = list.filter { (it.planeTailNumber ?: "").uppercase() == tail }
        }
        if (assignedToMe && me?.id != null) {
            list = list.filter { it.assignedUserId == me.id }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Section toggle: Assigned Tasks <-> Plane Progress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.06f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TaskSection.values().forEach { s ->
                val sel = s == section
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) HFColors.OnSurface.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { section = s }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        s.label,
                        color = if (sel) HFColors.OnSurface else HFColors.OnSurface.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.size(14.dp))

        // Plane filter chips — All Planes + each plane tail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipPill(
                label = "All Planes",
                selected = planeFilter.isBlank(),
                onClick = { planeFilter = "" }
            )
            state.planes.forEach { plane ->
                FilterChipPill(
                    label = plane.tailNumber,
                    selected = planeFilter == plane.tailNumber,
                    onClick = { planeFilter = plane.tailNumber }
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        when (section) {
            TaskSection.Assigned -> {
                // Assigned-to-me toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (assignedToMe) HFColors.StatusBlue.copy(alpha = 0.12f)
                            else HFColors.OnSurface.copy(alpha = 0.04f)
                        )
                        .border(
                            1.dp,
                            if (assignedToMe) HFColors.StatusBlue.copy(alpha = 0.45f)
                            else HFColors.OnSurface.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { assignedToMe = !assignedToMe }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Assigned to me",
                        color = if (assignedToMe) HFColors.StatusBlue else HFColors.OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (assignedToMe) "ON" else "OFF",
                        color = if (assignedToMe) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.size(10.dp))

                // + New Task — techs can create a standalone task (not just
                // convert a squawk). Pre-fills the plane if one is filtered.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HFColors.StatusGreen.copy(alpha = 0.12f))
                        .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { showCreateSheet = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ New Task",
                        color = HFColors.StatusGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.size(12.dp))

                if (filteredTasks.isEmpty()) {
                    EmptyTasksPanel(message = "No tasks for this filter yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                expanded = expandedTaskId == task.id,
                                onToggle = {
                                    expandedTaskId = if (expandedTaskId == task.id) null else task.id
                                },
                                onEdit = { editingTask = task }
                            )
                        }
                    }
                }
            }

            TaskSection.Progress -> {
                val planesToShow = remember(state.planes, planeFilter) {
                    if (planeFilter.isBlank()) state.planes
                    else state.planes.filter { it.tailNumber == planeFilter }
                }
                if (planesToShow.isEmpty()) {
                    EmptyTasksPanel(message = "No planes available for this filter.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(planesToShow, key = { it.id }) { plane ->
                            PlaneTaskProgressCard(
                                tail = plane.tailNumber,
                                displayName = plane.displayName,
                                tasks = tasks.filter {
                                    (it.planeTailNumber ?: "").equals(plane.tailNumber, ignoreCase = true)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.size(28.dp))
    }
}

private enum class TaskSection(val label: String) {
    Assigned("Assigned Tasks"),
    Progress("Plane Progress")
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(
                if (selected) HFColors.OnSurface.copy(alpha = 0.18f)
                else HFColors.OnSurface.copy(alpha = 0.04f)
            )
            .border(
                1.dp,
                if (selected) HFColors.OnSurface.copy(alpha = 0.30f) else HFColors.OnSurface.copy(alpha = 0.10f),
                RoundedCornerShape(100.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) HFColors.OnSurface else HFColors.OnSurface.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun TaskCard(
    task: HFTask,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .padding(16.dp)
    ) {
        // Header row: tail + status pill
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                task.planeTailNumber ?: "—",
                color = HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            StatusPill(status = task.status)
        }
        Spacer(Modifier.size(6.dp))
        Text(
            task.title,
            color = HFColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (task.details.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                task.details,
                color = HFColors.OnSurface.copy(alpha = 0.68f),
                fontSize = 12.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3
            )
        }

        // Expanded: status update grid (matches iOS assignedTaskStatusEditor)
        if (expanded) {
            Spacer(Modifier.size(12.dp))
            Text(
                "Update Status",
                color = HFColors.OnSurface.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("open", "inProgress", "waitingOnParts", "done").forEach { s ->
                    StatusButton(
                        label = statusLabel(s),
                        isSelected = task.status == s,
                        accent = statusAccent(s),
                        onClick = { SharedStore.updateTaskStatus(task.id, s) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.08f))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Edit Task", color = HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusButton(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accent else HFColors.OnSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (isSelected) Color.Black else HFColors.OnSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusPill(status: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(statusAccent(status).copy(alpha = 0.90f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            statusLabel(status),
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlaneTaskProgressCard(
    tail: String,
    displayName: String,
    tasks: List<HFTask>
) {
    val open = tasks.count { it.status != "done" }
    val inProgress = tasks.count { it.status == "inProgress" }
    val waiting = tasks.count { it.status == "waitingOnParts" }
    val done = tasks.count { it.status == "done" }
    val next = tasks.firstOrNull { it.status != "done" }?.title

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tail, color = HFColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                displayName,
                color = HFColors.OnSurface.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountChip("Open $open", HFColors.StatusOrange)
            if (inProgress > 0) CountChip("In Progress $inProgress", HFColors.StatusBlue)
            if (waiting > 0) CountChip("Waiting $waiting", HFColors.StatusYellow)
            CountChip("Done $done", HFColors.StatusGreen)
        }
        if (next != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                "Next: $next",
                color = HFColors.OnSurface.copy(alpha = 0.68f),
                fontSize = 12.sp
            )
        } else if (tasks.isEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(
                "No HF tasks for this plane yet.",
                color = HFColors.OnSurface.copy(alpha = 0.50f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CountChip(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(accent.copy(alpha = 0.90f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyTasksPanel(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Text(
            "Tasks",
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.size(8.dp))
        Text(message, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusLabel(status: String): String = when (status) {
    "open" -> "Open"
    "inProgress" -> "In Progress"
    "waitingOnParts" -> "Waiting"
    "done" -> "Done"
    else -> status.replaceFirstChar { it.uppercase() }
}

private fun statusAccent(status: String): Color = when (status) {
    "open" -> HFColors.StatusOrange
    "inProgress" -> HFColors.StatusBlue
    "waitingOnParts" -> HFColors.StatusYellow
    "done" -> HFColors.StatusGreen
    else -> HFColors.OnSurface.copy(alpha = 0.30f)
}

/** Create or edit a task. `existing == null` => create; otherwise edit.
 *  Plane is optional; assignee defaults to the current user on create. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditSheet(existing: HFTask?, onDismiss: () -> Unit) {
    val state by SharedStore.state.collectAsState()
    val me = state.currentUser
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var planeId by remember { mutableStateOf(existing?.planeId) }
    var planeTail by remember { mutableStateOf(existing?.planeTailNumber ?: "") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var details by remember { mutableStateOf(existing?.details ?: "") }
    var category by remember { mutableStateOf(HFWorkCategory.fromRaw(existing?.category)) }
    var assigneeId by remember { mutableStateOf(existing?.assignedUserId ?: me?.id) }
    var assigneeName by remember { mutableStateOf(existing?.assignedUserName ?: me?.displayName) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Background,
        contentColor = HFColors.OnSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                if (existing == null) "New Task" else "Edit Task",
                color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(16.dp))

            // Plane (optional) — chip row.
            SheetLabel("Plane (optional)")
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChipPill(label = "None", selected = planeId == null) { planeId = null; planeTail = "" }
                state.planes.forEach { p ->
                    FilterChipPill(label = p.tailNumber, selected = p.id == planeId) {
                        planeId = p.id; planeTail = p.tailNumber
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            SheetField("Title", title, { title = it }, "Replace landing light")
            Spacer(Modifier.size(12.dp))
            SheetField("Details", details, { details = it }, "Notes (optional)", singleLine = false)

            Spacer(Modifier.size(12.dp))
            SheetLabel("Category")
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HFWorkCategory.values().forEach { cat ->
                    FilterChipPill(label = cat.label, selected = cat == category) { category = cat }
                }
            }

            Spacer(Modifier.size(12.dp))
            SheetLabel("Assign To")
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChipPill(label = "Unassigned", selected = assigneeId == null) {
                    assigneeId = null; assigneeName = null
                }
                state.users.forEach { u ->
                    FilterChipPill(label = u.displayName.ifBlank { u.email }, selected = u.id == assigneeId) {
                        assigneeId = u.id; assigneeName = u.displayName.ifBlank { u.email }
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.size(10.dp))
                Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp)
            }

            Spacer(Modifier.size(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HFColors.StatusGreen.copy(alpha = if (busy || title.isBlank()) 0.06f else 0.16f))
                    .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable(enabled = !busy && title.isNotBlank()) {
                        busy = true; error = null
                        scope.launch {
                            val r = if (existing == null) {
                                SharedStore.createTask(
                                    planeId, planeTail.takeIf { it.isNotBlank() },
                                    title, details, category.raw, assigneeId, assigneeName
                                )
                            } else {
                                SharedStore.updateTask(
                                    existing.id, title, details, category.raw, assigneeId, assigneeName
                                )
                            }
                            when (r) {
                                SharedStore.CreateResult.Success -> onDismiss()
                                is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "Saving…" else if (existing == null) "Create Task" else "Save Changes",
                    color = HFColors.StatusGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        color = HFColors.OnSurface.copy(alpha = 0.60f),
        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp
    )
}

@Composable
private fun SheetField(
    label: String, value: String, onChange: (String) -> Unit,
    placeholder: String, singleLine: Boolean = true
) {
    Column {
        SheetLabel(label)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(), singleLine = singleLine,
            placeholder = { Text(placeholder, color = HFColors.OnSurface.copy(alpha = 0.35f), fontSize = 13.sp) },
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
}
