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
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
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
import kotlin.math.roundToInt
import com.hangarflow.app.R
import androidx.compose.ui.res.stringResource
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.cloud.HFTroubleshootRun
import com.hangarflow.app.data.cloud.TroubleshootCloud
import com.hangarflow.app.data.cloud.TroubleshootStarter
import com.hangarflow.app.ui.guided.TroubleshootScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Squawks hub. Lists every open squawk with its plane tail, title,
 * notes, status pill, and any attached photos. Photos are pulled from
 * the private `squawk-photos` bucket via short-lived signed URLs and
 * rendered with Coil so the list stays snappy.
 */
@Composable
fun SquawksHub() {
    var showCreate by remember { mutableStateOf(false) }
    var editingSquawk by remember { mutableStateOf<HFSquawk?>(null) }

    // A squawk is a SYMPTOM, which is exactly what a troubleshooting table is
    // indexed by — so the complaint, already here in the crew's own words, is
    // where chasing the fault starts.
    var troubleshootRun by remember { mutableStateOf<HFTroubleshootRun?>(null) }
    var troubleshootBusy by remember { mutableStateOf(false) }
    var troubleshootNote by remember { mutableStateOf<String?>(null) }
    val tsScope = rememberCoroutineScope()
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
    troubleshootRun?.let { r ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { troubleshootRun = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
        ) {
            TroubleshootScreen(run = r, onClose = { troubleshootRun = null })
        }
    }

    troubleshootNote?.let { msg ->
        AlertDialog(
            onDismissRequest = { troubleshootNote = null },
            confirmButton = {
                TextButton(onClick = { troubleshootNote = null }) { Text("OK") }
            },
            text = { Text(msg) },
        )
    }

    editingSquawk?.let { sq ->
        // Re-resolve against live state so the sheet reflects the latest
        // corrective-action stamp after a save round-trip.
        val live = SharedStore.state.collectAsState().value.squawks.firstOrNull { it.id == sq.id } ?: sq
        EditSquawkSheet(squawk = live, onDismiss = { editingSquawk = null })
        return
    }
    HFPullToRefreshHost {
        SquawksHubContent(
            onOpenCreate = { showCreate = true },
            onOpenEdit = { editingSquawk = it },
            onTroubleshoot = { sq ->
                tsScope.launch {
                    troubleshootBusy = true
                    val orgId = AuthManager.state.value.orgId
                    if (orgId.isNullOrBlank()) {
                        troubleshootBusy = false
                        troubleshootNote = "No shop."
                        return@launch
                    }
                    // Reuse an open run for this squawk rather than starting a
                    // second one: what was already ruled out is the whole value
                    // of the record, and a fresh run would discard it.
                    val existing = withContext(Dispatchers.IO) {
                        runCatching { TroubleshootCloud.fetch(orgId) }
                            .getOrDefault(emptyList())
                            .firstOrNull { it.squawkId == sq.id && !it.isClosed }
                    }
                    if (existing != null) {
                        troubleshootBusy = false
                        troubleshootRun = existing
                        return@launch
                    }

                    // This used to send the mechanic to the desktop, and the
                    // desktop had no way to start one either — Kotlin could
                    // fetch, answer and close a tree but never create one, so
                    // `hf_troubleshoot_runs` was empty in every shop and this
                    // screen rendered something nothing could produce.
                    val me = SharedStore.state.value.currentUser
                    val (runId, err) = withContext(Dispatchers.IO) {
                        TroubleshootStarter.start(
                            orgId = orgId,
                            symptom = sq.title,
                            squawkId = sq.id,
                            workLogId = null,
                            planeId = sq.planeId,
                            actorId = me?.authUserId,
                            actorName = me?.displayName,
                        )
                    }
                    // Re-read rather than trusting the insert's echo: the tree
                    // a mechanic works from should be the row the database
                    // actually holds.
                    val fetched = if (runId == null) null else withContext(Dispatchers.IO) {
                        runCatching { TroubleshootCloud.fetch(orgId) }
                            .getOrDefault(emptyList())
                            .firstOrNull { it.id == runId }
                    }
                    troubleshootBusy = false
                    troubleshootNote = err
                    troubleshootRun = fetched
                }
            },
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
    onOpenEdit: (HFSquawk) -> Unit,
    onTroubleshoot: (HFSquawk) -> Unit = {},
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
                stringResource(R.string.sq_new),
                color = HFColors.BrandInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Send a list to the aircraft owner for authorisation.
        //
        // ADMIN ONLY on a phone. The desktop lets a lead tech send; here it
        // is admins only. Choosing which items go in front of a customer and
        // typing their address is desk work, and the narrower rule is the
        // safer default on the device most likely to be sitting unlocked on
        // a bench. The button is not the gate — the edge function checks the
        // role and RLS backs it up — but offering an action that will be
        // refused is the bug we keep finding.
        val authState = AuthManager.state.collectAsState().value
        var sendingApproval by remember { mutableStateOf(false) }
        // Only items the owner has not already ruled on. Re-sending a list
        // including decisions they already made reads as "we ignored you".
        val pendingForOwner = remember(squawks) {
            squawks.filter { it.ownerDecision == null && it.planeTailNumber.isNotBlank() }
        }
        if (authState.isAdmin && pendingForOwner.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HFColors.StatusOrange.copy(alpha = 0.14f))
                    .clickable { sendingApproval = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Send, null,
                    tint = HFColors.StatusOrange, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.sq_send_owner),
                    color = HFColors.StatusOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (sendingApproval) {
            SendForApprovalDialog(
                candidates = pendingForOwner,
                onDismiss = { sendingApproval = false }
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
            color = HFColors.ink(0.5f),
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
                        onEdit = { onOpenEdit(squawk) },
                        onTroubleshoot = { onTroubleshoot(squawk) },
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
            title = { Text(stringResource(R.string.sq_delete_q), color = HFColors.StatusRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${squawk.title.ifBlank { "Untitled squawk" }}\" will be removed for everyone. This can't be undone.",
                    color = HFColors.ink(0.8f)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = squawk.id
                    deleteScope.launch { SharedStore.deleteSquawk(id) }
                    deleteConfirmFor = null
                }) { Text(stringResource(R.string.action_delete), color = HFColors.StatusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteConfirmFor = null }) {
                    Text(stringResource(R.string.action_cancel), color = HFColors.ink(0.7f))
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
            stringResource(R.string.sq_change_status).uppercase(),
            color = HFColors.ink(0.55f),
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
                    .background(if (isCurrent) color.copy(alpha = 0.14f) else HFColors.fill(0.04f))
                    .border(
                        1.dp,
                        if (isCurrent) color.copy(alpha = 0.45f) else HFColors.stroke(0.10f),
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
        color = HFColors.ink(0.5f),
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
            .background(HFColors.fill(0.06f))
            .border(1.dp, HFColors.stroke(0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = HFColors.ink(0.55f),
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
                    stringResource(R.string.sq_search),
                    color = HFColors.ink(0.4f),
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
                    contentDescription = stringResource(R.string.action_clear),
                    tint = HFColors.ink(0.5f),
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
    val bg = HFColors.fill(if (isSelected) 0.16f else 0.06f)
    val border = HFColors.stroke(if (isSelected) 0.5f else 0.12f)
    val fg = HFColors.ink(if (isSelected) 1f else 0.75f)
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
    onEdit: () -> Unit,
    onTroubleshoot: () -> Unit = {},
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
            .background(HFColors.fill(0.05f))
            .border(1.dp, HFColors.stroke(0.12f), RoundedCornerShape(18.dp))
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
            // A squawk is a symptom, and a troubleshooting table is indexed by
            // symptom. This is the natural place to start chasing it.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTroubleshoot)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Troubleshoot", color = HFColors.StatusCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(2.dp))
            // Full edit — title/plane/category/notes/corrective action.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.action_edit), color = HFColors.ink(0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(2.dp))
            // Anyone (including techs) can delete a squawk.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.action_delete), color = HFColors.StatusRed.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                color = HFColors.ink(0.72f),
                fontSize = 13.sp,
                maxLines = 4
            )
        }

        // ── What the aircraft owner said ──
        //
        // Without this the approval loop is one-directional: an owner could
        // decline work and nobody in the shop would see it unless they
        // queried the database. The reason is shown verbatim — "not this
        // visit" and "that was done at the last annual" need completely
        // different responses from the shop.
        squawk.ownerDecision?.let { decision ->
            val approved = decision == "approved"
            val tint = if (approved) HFColors.StatusGreen else HFColors.StatusRed
            Spacer(Modifier.size(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.10f))
                    .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    if (approved) "OWNER APPROVED THIS WORK" else "OWNER DECLINED THIS WORK",
                    color = tint, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                if (!squawk.ownerDecisionReason.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(squawk.ownerDecisionReason, color = HFColors.ink(0.80f), fontSize = 13.sp)
                }
                if (!squawk.ownerDecidedBy.isNullOrBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(squawk.ownerDecidedBy, color = HFColors.ink(0.45f), fontSize = 10.sp)
                }
            }
        }

        // ── Corrective action — what was done to fix it ──
        if (squawk.correctiveAction.isNotBlank()) {
            Spacer(Modifier.size(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HFColors.StatusGreen.copy(alpha = 0.07f))
                    .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    stringResource(R.string.sq_corrective_caps),
                    color = HFColors.StatusGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.size(4.dp))
                Text(squawk.correctiveAction, color = HFColors.OnSurface, fontSize = 13.sp)
                val by = squawk.correctedByUserName.takeIf { it.isNotBlank() }
                val on = squawk.correctedAt?.take(10)
                if (by != null || on != null) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        listOfNotNull(by?.let { "Closed by $it" }, on).joinToString(" · "),
                        color = HFColors.ink(0.5f),
                        fontSize = 10.sp
                    )
                }
            }
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

        // AI-suggested manual pages for this squawk (on-demand, tap to expand).
        Spacer(Modifier.size(10.dp))
        RelatedManualsSection(squawk)

        // Status footer — tap the pill to change status. Neutral capsule
        // with a colored dot + label, the iOS card-footer treatment.
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(color = statusColor, label = statusLabel, onClick = onTapStatus)
        }
    }
}

/**
 * Collapsible "Related in Manuals" section on a squawk card. Loads lazily the
 * first time it's expanded (so we don't fire a lookup for every card in the
 * list) via the `squawk-manuals` edge function — semantic + keyword match.
 */
@Composable
private fun RelatedManualsSection(squawk: HFSquawk) {
    val cloud = remember { HFCloudSyncService() }
    var expanded by remember(squawk.id) { mutableStateOf(false) }
    var loading by remember(squawk.id) { mutableStateOf(false) }
    var loaded by remember(squawk.id) { mutableStateOf(false) }
    var refs by remember(squawk.id) { mutableStateOf<List<HFCloudSyncService.SquawkManualRef>>(emptyList()) }

    LaunchedEffect(expanded) {
        if (expanded && !loaded && !loading) {
            val text = "${squawk.title} ${squawk.notes}".trim()
            if (text.length >= 3) {
                loading = true
                refs = runCatching {
                    cloud.squawkManuals(text = text, planeTail = squawk.planeTailNumber.ifBlank { null })
                }.getOrNull()?.manualRefs ?: emptyList()
                loading = false
            }
            loaded = true
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.sq_related_manuals), color = HFColors.StatusCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), color = HFColors.StatusCyan, strokeWidth = 2.dp)
            } else {
                Text(if (expanded) "▲" else "▼", color = HFColors.ink(0.4f), fontSize = 10.sp)
            }
        }
        if (expanded) {
            if (refs.isNotEmpty()) {
                refs.take(5).forEach { r ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HFColors.StatusCyan.copy(alpha = 0.06f))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.referenceCode ?: "—", color = HFColors.StatusCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            r.pageLabel?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.width(6.dp))
                                Text("p. $it", color = HFColors.ink(0.5f), fontSize = 11.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            r.similarity?.let {
                                Text("${(it * 100).roundToInt()}%", color = HFColors.ink(0.4f), fontSize = 10.sp)
                            }
                        }
                        r.title?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = HFColors.ink(0.8f), fontSize = 12.sp, maxLines = 2)
                        }
                        r.sourceManualName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = HFColors.ink(0.4f), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            } else if (loaded && !loading) {
                Text(stringResource(R.string.sq_no_related), color = HFColors.ink(0.4f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
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
            .background(HFColors.fill(0.06f))
            .border(1.dp, HFColors.stroke(0.10f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        if (signedUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(signedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_squawk_photo),
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
            .background(HFColors.fill(0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = HFColors.ink(0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(color: Color, label: String, onClick: () -> Unit) {
    // Neutral capsule with a colored status dot — clean & monochrome,
    // tappable to change the status.
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(HFColors.fill(0.10f))
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
        Text(label, color = HFColors.ink(0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun statusPresentation(raw: String): Pair<String, Color> = when (raw) {
    "open" -> "Open" to HFColors.StatusBlue
    "inProgress" -> "In Progress" to HFColors.StatusYellow
    "waitingOnParts" -> "Waiting" to HFColors.StatusOrange
    "resolved" -> "Resolved" to HFColors.StatusGreen
    "deferred" -> "Deferred" to HFColors.ink(0.5f)
    "convertedToTask" -> "Converted" to HFColors.StatusPurple
    else -> raw.replaceFirstChar { it.titlecase() } to HFColors.ink(0.6f)
}

/// Send a squawk list to the aircraft owner for authorisation.
///
/// The owner has no account and never gets one — they receive a link. The
/// token is made server-side by `squawk-approval-send`; a client that chose
/// its own could choose a predictable one, and that token is the only thing
/// protecting a customer's aircraft data.
///
/// ONE LINK COVERS ONE AIRCRAFT, so this picks a tail first. The phone has
/// no plane filter above the list the way the desktop does, and mixing tails
/// would let an owner authorise work on an aeroplane that is not theirs —
/// the function refuses it, and asking here is kinder than being refused.
@Composable
private fun SendForApprovalDialog(
    candidates: List<HFSquawk>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Tails that actually have something awaiting a decision, most first.
    val byTail = remember(candidates) {
        candidates.groupBy { it.planeTailNumber }.toList().sortedByDescending { it.second.size }
    }
    var tail by remember { mutableStateOf(byTail.firstOrNull()?.first ?: "") }
    val forTail = remember(byTail, tail) { byTail.firstOrNull { it.first == tail }?.second ?: emptyList() }

    var email by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(30) }
    var sending by remember { mutableStateOf(false) }
    var sentUrl by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Resolved up here: stringResource is @Composable and these are read
    // from inside a coroutine.
    val mailFailedText = stringResource(R.string.sq_send_mail_failed)
    val sendFailedText = stringResource(R.string.sq_send_failed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sq_send_owner), color = HFColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sentUrl != null) {
                    Text(
                        stringResource(R.string.sq_send_sent),
                        color = HFColors.StatusGreen, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    note?.let { Text(it, color = HFColors.StatusOrange, fontSize = 12.sp) }
                    Text(
                        stringResource(R.string.sq_send_link_hint),
                        color = HFColors.OnSurfaceMuted, fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = sentUrl!!, onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                } else {
                    if (byTail.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            byTail.forEach { (t, list) ->
                                FilterChip(
                                    selected = t == tail,
                                    onClick = { tail = t },
                                    label = { Text("$t (${list.size})") }
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.sq_send_awaiting, tail, forTail.size),
                        color = HFColors.OnSurfaceMuted, fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text(stringResource(R.string.sq_send_owner_email)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { d ->
                            FilterChip(
                                selected = days == d, onClick = { days = d },
                                label = { Text(stringResource(R.string.sq_send_days, d)) }
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.sq_send_no_prices),
                        color = HFColors.OnSurfaceFaint, fontSize = 11.sp
                    )
                    error?.let { Text(it, color = HFColors.StatusRed, fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            if (sentUrl != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            } else {
                Button(
                    enabled = !sending && forTail.isNotEmpty() && email.contains("@"),
                    onClick = {
                        sending = true; error = null
                        scope.launch {
                            val r = runCatching {
                                withContext(Dispatchers.IO) {
                                    SharedStore.sendSquawkApproval(
                                        planeId = forTail.firstOrNull()?.planeId,
                                        squawkIds = forTail.map { it.id },
                                        toEmail = email.trim(),
                                        expiresDays = days
                                    )
                                }
                            }
                            r.onSuccess { out ->
                                sentUrl = out.url
                                // The link EXISTS even if the mail failed —
                                // say which happened rather than let the shop
                                // believe an owner was emailed.
                                if (!out.emailed) {
                                    note = out.email_error ?: mailFailedText
                                }
                            }.onFailure {
                                error = it.message ?: sendFailedText
                            }
                            sending = false
                        }
                    }
                ) { Text(if (sending) "…" else stringResource(R.string.sq_send_action)) }
            }
        },
        dismissButton = {
            if (sentUrl == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        containerColor = HFColors.Surface
    )
}
