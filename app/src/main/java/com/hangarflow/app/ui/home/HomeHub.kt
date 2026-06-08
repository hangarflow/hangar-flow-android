package com.hangarflow.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.liveWorkedMinutes
import com.hangarflow.app.data.model.HFUserProfile
import com.hangarflow.app.data.model.HFWorkLogStatus
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The tech-first home screen — an exact visual port of the iOS
 * `IOSPlanesHubView` body (header + Live View panel + card grid).
 * No tabs, no Material chrome. Cards open feature sheets.
 */
@Composable
fun HomeHub(onOpenHub: (HomeDestination) -> Unit) {
    HFPullToRefreshHost {
        HomeHubContent(onOpenHub = onOpenHub)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeHubContent(onOpenHub: (HomeDestination) -> Unit) {
    val authState by AuthManager.state.collectAsState()
    val shopState by SharedStore.state.collectAsState()
    val activeShift by SharedStore.activeShift.collectAsState()

    val me = shopState.currentUser
    val isAdmin = authState.isAdmin

    // Presents IOSClockOutSheet when the tech taps Clock Out, so they
    // can log a summary + reimbursements before the shift closes.
    var showClockOutSheet by remember { mutableStateOf(false) }
    if (showClockOutSheet) {
        com.hangarflow.app.ui.hubs.ClockOutSheet(onDismiss = { showClockOutSheet = false })
        return
    }

    val openAssignedCount = openAssignedCount(shopState, me, isAdmin)
    val baseTodayMinutes = todayMinutes(shopState, me, isAdmin)

    // Tick every 30 seconds while working so the Today tile keeps
    // climbing — mirrors iOS LiveView's real-time hours display. Stops
    // ticking during lunch because the live-minutes math excludes it.
    var nowTick by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(activeShift?.phase) {
        while (activeShift != null && activeShift?.phase == com.hangarflow.app.data.ShiftPhase.Working) {
            nowTick = Instant.now()
            kotlinx.coroutines.delay(30_000)
        }
        // One final tick so the moment you flip to lunch the frozen
        // minute count is accurate.
        nowTick = Instant.now()
    }
    val liveShiftMinutes = activeShift?.liveWorkedMinutes(nowTick) ?: 0
    val displayedTodayMinutes = baseTodayMinutes + liveShiftMinutes

    // Map the shift to the 4-phase button label.
    val clockPhase = when {
        activeShift == null -> com.hangarflow.app.ui.home.ClockPhase.Idle
        activeShift!!.phase == com.hangarflow.app.data.ShiftPhase.OnLunch ->
            com.hangarflow.app.ui.home.ClockPhase.OnLunch
        activeShift!!.lunchTaken -> com.hangarflow.app.ui.home.ClockPhase.ReadyToClockOut
        else -> com.hangarflow.app.ui.home.ClockPhase.Working
    }
    val openSquawks = shopState.squawks.count {
        it.status != "resolved" && it.status != "convertedToTask"
    }
    // Match iOS: only count `manualPDF` + `manualText` rows (skip the "pdf"
    // work-package entries that share the same table) and dedupe by
    // (tail, file name) so a re-uploaded manual doesn't double-count.
    val manualsCount = shopState.manuals
        .filter { it.sourceType == "manualPDF" || it.sourceType == "manualText" }
        .distinctBy { "${it.planeTailNumber?.uppercase()}:${it.fileName.lowercase()}" }
        .size
    val isOffline = authState.error?.isNotBlank() == true ||
        shopState.error?.isNotBlank() == true

    val context = LocalContext.current
    // Lead techs see the same cards as admins (incl. Users) — they manage
    // the roster too. Owner-only powers are gated inside each hub.
    val defaultCards = cardsForRole(authState.isAdmin || authState.isLeadTech)
    val savedOrder = remember { HomeCardPreferences.loadOrder(context) }
    val orderedIds = remember(defaultCards, savedOrder) {
        HomeCardPreferences.applyOrder(defaultCards.map { it.id }, savedOrder).toMutableList()
    }
    val orderState = remember { mutableStateOf(orderedIds.toList()) }
    val cards = orderState.value.mapNotNull { id -> defaultCards.firstOrNull { it.id == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // Tech sees their own first name big; admin sees the org name.
        // Subtitle shows the complementary label (role for tech, "Admin"
        // badge on Hangar Flow for the admin account).
        val title = when {
            !isAdmin && !me?.displayName.isNullOrBlank() ->
                me!!.displayName.substringBefore(' ').trim().ifBlank { me.displayName }
            else -> authState.orgName.ifBlank { "Hangar Flow" }
        }
        val subtitle = when {
            !isAdmin -> authState.orgName.takeIf { it.isNotBlank() }
            else -> "Admin"
        }
        IOSHomeHeader(
            primaryTitle = title,
            subtitle = subtitle,
            syncStatus = if (shopState.loading) "Checking" else "Active",
            onGoHome = { /* already home */ },
            onOpenSettings = { onOpenHub(HomeDestination.Settings) },
            onSignOut = { AuthManager.signOut() }
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IOSLiveViewPanel(
                userName = me?.displayName,
                syncLabel = friendlySyncLabel(shopState.loading, shopState.error),
                clockPhase = clockPhase,
                onClockAction = {
                    when (clockPhase) {
                        ClockPhase.Idle -> SharedStore.clockIn()
                        ClockPhase.Working -> SharedStore.lunchOut()
                        ClockPhase.OnLunch -> SharedStore.lunchIn()
                        // Show the summary + reimbursements sheet; it
                        // calls SharedStore.clockOut() on submit so the
                        // shift closes only after the receipts upload.
                        ClockPhase.ReadyToClockOut -> { showClockOutSheet = true }
                    }
                },
                onSkipLunch = { SharedStore.skipLunch() },
                todayHoursLabel = formatHours(displayedTodayMinutes),
                openAssignedCount = openAssignedCount,
                manualsCount = manualsCount,
                openSquawkCount = openSquawks,
                isOffline = isOffline,
                onOpenTimeCard = { onOpenHub(HomeDestination.TimeCard) },
                onOpenWorkLogs = { onOpenHub(HomeDestination.WorkLogs) },
                onOpenManuals = { onOpenHub(HomeDestination.Manuals) },
                onOpenSquawks = { onOpenHub(HomeDestination.Squawks) }
            )

            // Long-press a card to lift it; drag onto another card to
            // swap positions. New order persists immediately.
            ReorderableHomeGrid(
                cards = cards,
                onTapCard = { onOpenHub(it.destination) },
                onSwap = { fromId, toId ->
                    val current = orderState.value.toMutableList()
                    val a = current.indexOf(fromId)
                    val b = current.indexOf(toId)
                    if (a >= 0 && b >= 0 && a != b) {
                        val tmp = current[a]
                        current[a] = current[b]
                        current[b] = tmp
                        orderState.value = current
                        HomeCardPreferences.saveOrder(context, current)
                    }
                }
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * 2-column home card grid with long-press drag-to-swap. Press and hold
 * any card → it lifts (scales up + shadows). Drag it over another card
 * → release → the two swap positions and the new order is persisted
 * via `HomeCardPreferences`.
 */
@Composable
private fun ReorderableHomeGrid(
    cards: List<HomeCard>,
    onTapCard: (HomeCard) -> Unit,
    onSwap: (fromId: String, toId: String) -> Unit
) {
    // Track each card's bounds in the root coordinate space so we can
    // hit-test where the user releases.
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerGlobalPos by remember { mutableStateOf(Offset.Zero) }

    // 3 cards per row to match the iOS iPad dashboard layout. Each card
    // uses weight(1f), so the Row auto-divides into thirds. Trailing rows
    // with fewer than 3 cards get spacer fillers so card width stays
    // consistent across rows.
    val rows = cards.chunked(3)
    rows.forEachIndexed { index, rowPair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowPair.forEach { card ->
                val isDragged = draggedId == card.id
                val isHovered = draggedId != null && draggedId != card.id &&
                    cardBounds[card.id]?.contains(pointerGlobalPos) == true

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            cardBounds[card.id] = coords.boundsInRoot()
                        }
                        .pointerInput(card.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { localOffset ->
                                    draggedId = card.id
                                    dragOffset = Offset.Zero
                                    val origin = cardBounds[card.id]?.topLeft ?: Offset.Zero
                                    pointerGlobalPos = origin + localOffset
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    dragOffset += drag
                                    pointerGlobalPos += drag
                                },
                                onDragEnd = {
                                    val from = draggedId
                                    val target = cardBounds.entries.firstOrNull { (id, rect) ->
                                        id != from && rect.contains(pointerGlobalPos)
                                    }?.key
                                    if (from != null && target != null) onSwap(from, target)
                                    draggedId = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    draggedId = null
                                    dragOffset = Offset.Zero
                                }
                            )
                        }
                        .graphicsLayer {
                            if (isDragged) {
                                scaleX = 1.04f
                                scaleY = 1.04f
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                shadowElevation = 24f
                                alpha = 0.96f
                            }
                        }
                        .zIndex(if (isDragged) 10f else 0f)
                ) {
                    IOSHomeFeatureCard(
                        title = card.title,
                        subtitle = card.subtitle,
                        icon = card.icon,
                        accent = if (isHovered) HFColors.BrandWhite else card.accent,
                        onClick = { onTapCard(card) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            // Pad short trailing row so cards keep one-third width.
            repeat(3 - rowPair.size) {
                Spacer(Modifier.weight(1f))
            }
        }
        if (index != rows.lastIndex) {
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun CustomizeHomeButton(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(
                1.dp,
                HFColors.OnSurface.copy(alpha = 0.12f),
                androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            "Customize home",
            color = HFColors.OnSurface.copy(alpha = 0.80f),
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

@androidx.compose.runtime.Composable
@androidx.compose.material3.ExperimentalMaterial3Api
private fun CustomizeHomeSheet(
    allCards: List<HomeCard>,
    hidden: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val working = remember { mutableStateOf(hidden) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Surface,
        contentColor = HFColors.OnSurface
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            androidx.compose.material3.Text(
                "Customize home".uppercase(),
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            androidx.compose.material3.Text(
                "Toggle which cards show on your home screen.",
                color = HFColors.OnSurface.copy(alpha = 0.60f),
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            Spacer(Modifier.size(14.dp))

            allCards.forEach { card ->
                val isVisible = card.id !in working.value
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.04f))
                        .border(
                            1.dp,
                            HFColors.OnSurface.copy(alpha = 0.08f),
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            working.value = if (isVisible) working.value + card.id
                            else working.value - card.id
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text(
                            card.title,
                            color = HFColors.OnSurface,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        androidx.compose.material3.Text(
                            card.subtitle,
                            color = HFColors.OnSurface.copy(alpha = 0.60f),
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                    // Lightweight toggle — same shape as NeedsPartsSection
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 22.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(100.dp))
                            .background(
                                if (isVisible) HFColors.StatusGreen
                                else HFColors.OnSurface.copy(alpha = 0.15f)
                            )
                            .padding(3.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(100.dp))
                                .background(HFColors.OnSurface)
                                .align(
                                    if (isVisible) androidx.compose.ui.Alignment.CenterEnd
                                    else androidx.compose.ui.Alignment.CenterStart
                                )
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
            }

            Spacer(Modifier.size(10.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(HFColors.BrandWhite)
                    .clickable { onSave(working.value) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Text(
                    "Done",
                    color = HFColors.BrandInk,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            Spacer(Modifier.size(20.dp))
        }
    }
}

enum class HomeDestination {
    Planes, WorkLogs, Tasks, Squawks, PartsToOrder, PartLocations, TimeCard, Manuals, FindParts, Settings, Users, Review, Schedule, ActivityLog
}

private data class HomeCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val destination: HomeDestination
)

private fun cardsForRole(isAdmin: Boolean): List<HomeCard> {
    // Default tech grid — exactly the 6 cards the iPad shows, in the
    // same order: Time Card, Planes, Squawks, Find Parts, Work Logs,
    // Manuals. Long-press any card on the home to drag-rearrange.
    val tech = listOf(
        // Order + copy mirror the iOS iPad dashboard exactly so usability
        // studies stay visually consistent across iPad and Android tablet.
        HomeCard(
            id = "timecard",
            title = "Time Card",
            subtitle = "Clean overview of time, hours, and recent activity",
            icon = Icons.Outlined.Timer,
            accent = HFColors.StatusGreen.copy(alpha = 0.44f),
            destination = HomeDestination.TimeCard
        ),
        HomeCard(
            id = "tasks",
            title = "Assigned Tasks / Progress",
            subtitle = "Admin-only task oversight and progress review",
            icon = Icons.Outlined.CheckCircle,
            accent = HFColors.StatusBlue.copy(alpha = 0.44f),
            destination = HomeDestination.Tasks
        ),
        HomeCard(
            id = "planes",
            title = "Planes",
            subtitle = "Open plane records and select an active aircraft",
            icon = Icons.Rounded.Flight,
            accent = HFColors.StatusBlue.copy(alpha = 0.50f),
            destination = HomeDestination.Planes
        ),
        HomeCard(
            id = "worklogs",
            title = "Work Logs",
            subtitle = "Plane logs and categorized maintenance history",
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            accent = HFColors.StatusCyan.copy(alpha = 0.44f),
            destination = HomeDestination.WorkLogs
        ),
        HomeCard(
            id = "squawks",
            title = "Squawk Lists",
            subtitle = "Add squawks and parts needs",
            icon = Icons.Outlined.ReportProblem,
            accent = HFColors.StatusOrange.copy(alpha = 0.44f),
            destination = HomeDestination.Squawks
        ),
        HomeCard(
            id = "findparts",
            title = "Find Parts",
            subtitle = "Search manuals for part numbers and references",
            icon = Icons.Outlined.Search,
            accent = HFColors.StatusCyan.copy(alpha = 0.44f),
            destination = HomeDestination.FindParts
        ),
        HomeCard(
            id = "manuals",
            title = "Manuals",
            subtitle = "Approved manual excerpts and PDFs",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            accent = HFColors.StatusPurple.copy(alpha = 0.44f),
            destination = HomeDestination.Manuals
        ),
        HomeCard(
            id = "partlocations",
            title = "Parts Inventory",
            subtitle = "Shared inventory — where parts live in the hangar",
            icon = Icons.Outlined.Inventory2,
            accent = HFColors.StatusYellow.copy(alpha = 0.44f),
            destination = HomeDestination.PartLocations
        ),
        HomeCard(
            id = "schedule",
            title = "Schedule",
            subtitle = "Plane drop-offs, RTS deadlines, and time-off",
            icon = Icons.Outlined.CalendarMonth,
            accent = HFColors.StatusBlue.copy(alpha = 0.44f),
            destination = HomeDestination.Schedule
        ),
        HomeCard(
            id = "activitylog",
            title = "Activity Log",
            subtitle = "Paper trail — who added, imported, or changed what",
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            accent = HFColors.OnSurface.copy(alpha = 0.30f),
            destination = HomeDestination.ActivityLog
        )
    )
    // Admins get a Users card on top of the tech grid so they can manage
    // the roster (invite is handled separately by the Admin FAB).
    if (isAdmin) {
        return tech + HomeCard(
            id = "users",
            title = "Users",
            subtitle = "Manage roles, invite teammates, deactivate access",
            icon = Icons.Outlined.PeopleAlt,
            accent = HFColors.StatusBlue.copy(alpha = 0.50f),
            destination = HomeDestination.Users
        )
    }
    return tech
}

// ---------- helpers ----------

private fun openAssignedCount(
    state: com.hangarflow.app.data.ShopState,
    me: HFUserProfile?,
    @Suppress("UNUSED_PARAMETER") isAdmin: Boolean
): Int {
    // Android tablet = tech-floor view. Home tile labelled "assigned to you"
    // should always reflect the current user's queue, regardless of role.
    // For org-wide totals an admin uses the Desktop dashboard.
    if (me == null) return 0
    return state.workLogs.count { log ->
        val mine = log.assignedUserId == me.id ||
            log.assignedUserName?.equals(me.displayName, ignoreCase = true) == true
        mine && HFWorkLogStatus.fromRaw(log.status) != HFWorkLogStatus.Done
    }
}

private fun todayMinutes(
    state: com.hangarflow.app.data.ShopState,
    me: HFUserProfile?,
    isAdmin: Boolean
): Int {
    val today = LocalDate.now(ZoneId.systemDefault())
    return state.timeEntries
        .filter {
            val date = runCatching {
                Instant.parse(it.entryDate).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull()
            date == today
        }
        .filter { entry ->
            if (isAdmin || me == null) true
            else entry.userName.equals(me.displayName, ignoreCase = true)
        }
        .sumOf { it.minutesWorked }
}

private fun formatHours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 && m == 0 -> "0h"
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private fun friendlySyncLabel(loading: Boolean, error: String?): String {
    if (loading) return "Syncing…"
    if (!error.isNullOrBlank()) return "Sync error"
    return "Synced"
}
