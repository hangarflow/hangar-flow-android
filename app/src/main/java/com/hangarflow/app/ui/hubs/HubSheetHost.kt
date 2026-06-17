package com.hangarflow.app.ui.hubs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.ui.home.HomeDestination
import com.hangarflow.app.ui.theme.HFColors

/**
 * Full-screen overlay hub sheet. Each `HomeDestination` gets its own
 * feature view — for now they're scaffolds that match the iOS
 * `IOSHubHeader` + body pattern. Phase 7b onward will replace each
 * body with the real iOS-ported content.
 */
@Composable
fun HubSheetHost(
    destination: HomeDestination,
    onDismiss: () -> Unit
) {
    // When a work log is selected from any hub, this state takes over
    // the entire host — including the parent header — so the user sees
    // a clean full-screen detail view.
    var openWorkLog by remember(destination) { mutableStateOf<HFWorkLog?>(null) }
    val onOpenWorkLog: (HFWorkLog) -> Unit = { openWorkLog = it }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
            // Consume all taps so they don't pass through to the home
            // cards rendered beneath this overlay.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        if (openWorkLog != null) {
            WorkLogManualViewer(
                log = openWorkLog!!,
                onClose = { openWorkLog = null }
            )
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            IOSHubHeader(
                title = destination.title,
                subtitle = destination.subtitle,
                onClose = onDismiss
            )
            when (destination) {
                HomeDestination.WorkLogs -> WorkLogsHub(onOpenWorkLog = onOpenWorkLog)
                HomeDestination.Planes -> PlanesHub(onOpenWorkLog = onOpenWorkLog)
                HomeDestination.Tasks -> TasksHub()
                HomeDestination.Squawks -> SquawksHub()
                HomeDestination.Manuals -> ManualsHub()
                HomeDestination.PartsToOrder -> PartsToOrderHub()
                HomeDestination.PartLocations -> PartsLocationHub()
                HomeDestination.FindParts -> FindPartsHub()
                HomeDestination.TimeCard -> TimeCardHub()
                HomeDestination.Settings -> SettingsHub()
                HomeDestination.Users -> UsersHub()
                HomeDestination.Schedule -> ScheduleHub()
                HomeDestination.ActivityLog -> ActivityLogHub()
                else -> Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    IOSPlaceholderPanel(message = destination.phaseMessage)
                    Spacer(Modifier.size(40.dp))
                }
            }
        }
    }
}

private val HomeDestination.title: String
    get() = when (this) {
        HomeDestination.Planes -> "Planes"
        HomeDestination.WorkLogs -> "Work Logs"
        HomeDestination.Tasks -> "Tasks"
        HomeDestination.Squawks -> "Squawks"
        HomeDestination.PartsToOrder -> "Parts to Order"
        HomeDestination.PartLocations -> "Parts Inventory"
        HomeDestination.TimeCard -> "Time Card"
        HomeDestination.Manuals -> "Manuals"
        HomeDestination.FindParts -> "Find Parts"
        HomeDestination.Settings -> "Settings"
        HomeDestination.Users -> "Users"
        HomeDestination.Review -> "Needs Review"
        HomeDestination.Schedule -> "Schedule"
        HomeDestination.ActivityLog -> "Activity Log"
    }

private val HomeDestination.subtitle: String
    get() = when (this) {
        HomeDestination.Planes -> "Aircraft currently in the shop."
        HomeDestination.WorkLogs -> "Open, assigned, and completed work."
        HomeDestination.Tasks -> "Tasks assigned to the shop and per-plane progress."
        HomeDestination.Squawks -> "File and track discrepancies."
        HomeDestination.PartsToOrder -> "Urgent, normal, and low priority parts."
        HomeDestination.PartLocations -> "Shared inventory — where parts live in the hangar."
        HomeDestination.TimeCard -> "Log time and review shifts."
        HomeDestination.Manuals -> "Manual references and full PDFs."
        HomeDestination.FindParts -> "Search manual text for part numbers."
        HomeDestination.Settings -> "Account, cards, and preferences."
        HomeDestination.Users -> "Roster, roles, and invitations."
        HomeDestination.Review -> "Imported rows awaiting approval."
        HomeDestination.Schedule -> "Plane drop-offs, RTS deadlines, time-off."
        HomeDestination.ActivityLog -> "Who added, imported, or changed what."
    }

private val HomeDestination.phaseMessage: String
    get() = "Coming in a follow-up phase — the iOS screen for this hub is being ported next."

/**
 * Port of iOS `IOSHubHeader`. Title + subtitle on the left, a circular
 * Close button on the right. Same padding / type weights as the iOS
 * version so the two platforms read identically.
 */
@Composable
fun IOSHubHeader(
    title: String,
    subtitle: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = HFColors.OnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
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

/**
 * Panel in the same visual style as iOS `IOSInfoPanel`. Used as the
 * "coming soon" body for hub sheets that haven't been ported yet.
 */
@Composable
fun IOSPlaceholderPanel(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "Placeholder",
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = message,
            color = HFColors.OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
