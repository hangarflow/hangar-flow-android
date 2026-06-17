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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.hangarflow.app.data.cloud.HFCloudSyncService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.liveWorkedMinutes
import com.hangarflow.app.data.model.HFTimeEntry
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun TimeCardHub() {
    var showRequestTimeOff by remember { mutableStateOf(false) }
    if (showRequestTimeOff) {
        RequestTimeOffSheet(onDismiss = { showRequestTimeOff = false })
        return
    }
    HFPullToRefreshHost {
        TimeCardHubContent(onRequestTimeOff = { showRequestTimeOff = true })
    }
}

@Composable
private fun TimeCardHubContent(onRequestTimeOff: () -> Unit = {}) {
    val shopState by SharedStore.state.collectAsState()
    val authState by AuthManager.state.collectAsState()
    val activeShift by SharedStore.activeShift.collectAsState()

    val me = shopState.currentUser

    // Admin → any tech (or "All Shop"). Tech → always themselves only.
    // `null` selectedUserId means "All Shop" (admins only).
    val defaultSelection: String? = me?.id
    var selectedUserId by remember(defaultSelection) {
        mutableStateOf<String?>(defaultSelection)
    }
    val isAdmin = authState.isAdmin

    // Resolve display name for the selected user so we can match entries
    // on either userId or userName (older rows may only have the name).
    val selectedUser = remember(selectedUserId, shopState.users) {
        shopState.users.firstOrNull { it.id == selectedUserId }
    }

    val entries = remember(shopState.timeEntries, selectedUserId, selectedUser) {
        val all = shopState.timeEntries
        val filtered = if (selectedUserId == null) all
        else all.filter { entry ->
            val byId = entry.userId == selectedUserId
            val byName = selectedUser?.displayName
                ?.takeIf { it.isNotBlank() }
                ?.equals(entry.userName, ignoreCase = true) == true
            byId || byName
        }
        filtered.sortedByDescending { it.entryDate }
    }

    val periods = remember(entries) { computePeriodTotals(entries) }

    // ---- History view state (mirrors iOS IOSTimeCardHubView History) ----
    // A local view filter only: it changes WHICH entries render, never the
    // underlying data. Defaults to Monthly like iOS.
    var historyPeriod by remember { mutableStateOf(HistoryPeriod.Monthly) }
    var historyUseRange by remember { mutableStateOf(false) }
    // Custom range, anchored to "now". Adjusted in whole-week steps via the
    // stepper chips so we stay functional without a date-picker dependency.
    var rangeWeeksBack by remember { mutableStateOf(4) }

    // The entries actually shown in History, after the period / range filter.
    val historyEntries = remember(entries, historyPeriod, historyUseRange, rangeWeeksBack) {
        filterHistory(entries, historyPeriod, historyUseRange, rangeWeeksBack)
    }
    val grouped = remember(historyEntries) { historyEntries.groupBy { it.entryDate.take(10) } }
    val historyTotalMinutes = remember(historyEntries) { historyEntries.sumOf { it.minutesWorked } }

    // AI hours-anomaly review (admin only) — flags entries worth a look.
    val anomCloud = remember { HFCloudSyncService() }
    val anomScope = rememberCoroutineScope()
    var anomalyFlags by remember { mutableStateOf<List<HFCloudSyncService.HoursFlag>>(emptyList()) }
    LaunchedEffect(isAdmin, entries.size, selectedUserId) {
        if (!isAdmin || entries.size < 2) { anomalyFlags = emptyList(); return@LaunchedEffect }
        val payload = entries.take(120).map {
            HFCloudSyncService.HoursAnomalyEntry(it.id, it.userName, it.entryDate, it.minutesWorked, it.notes)
        }
        anomalyFlags = runCatching { anomCloud.hoursAnomalies(payload) }.getOrDefault(emptyList())
    }

    // Activity breakdown visible to admins: work logs signed off and
    // squawks filed/resolved by the selected user, bucketed by day.
    // Techs don't see this — their view stays minimal.
    data class DayActivity(
        val completedWorkLogs: List<com.hangarflow.app.data.model.HFWorkLog>,
        val filedSquawks: List<com.hangarflow.app.data.model.HFSquawk>
    )

    val dayActivities: Map<String, DayActivity> = remember(
        isAdmin, selectedUserId, selectedUser, shopState.workLogs, shopState.squawks
    ) {
        if (!isAdmin) return@remember emptyMap()
        val userIdFilter = selectedUserId
        val userNameFilter = selectedUser?.displayName?.takeIf { it.isNotBlank() }

        fun matchesUser(userId: String?, userName: String?): Boolean {
            if (userIdFilter == null) return true
            if (userId == userIdFilter) return true
            if (userNameFilter != null && userName?.equals(userNameFilter, ignoreCase = true) == true) return true
            return false
        }

        val wlByDay = shopState.workLogs
            .filter { it.status == "done" && matchesUser(it.assignedUserId, it.assignedUserName) }
            .groupBy { (it.updatedAt ?: it.createdAt ?: "").take(10) }

        val sqByDay = shopState.squawks
            .filter { matchesUser(it.reportedByUserId, it.reportedByUserName) }
            .groupBy { (it.createdAt ?: "").take(10) }

        val allDays = (wlByDay.keys + sqByDay.keys).toSortedSet()
        allDays.associateWith { day ->
            DayActivity(
                completedWorkLogs = wlByDay[day] ?: emptyList(),
                filedSquawks = sqByDay[day] ?: emptyList()
            )
        }
    }

    // Merge activity-only days into the grouped time entries so days
    // where a tech worked but didn't clock in still appear for admins.
    val allDays = remember(grouped, dayActivities) {
        (grouped.keys + dayActivities.keys).toSortedSet().sortedDescending()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // People picker — admins see every tech in the org, plus an
        // "All Shop" pill that aggregates everyone's hours. Techs are
        // locked to their own row (no chips shown).
        if (isAdmin && shopState.users.isNotEmpty()) {
            PeoplePicker(
                users = shopState.users.sortedBy { it.displayName.lowercase() },
                currentUserId = me?.id,
                selectedUserId = selectedUserId,
                onSelect = { selectedUserId = it }
            )
            Spacer(Modifier.size(10.dp))
            // Title line: whose hours we're showing.
            Text(
                when {
                    selectedUserId == null -> "All Shop"
                    selectedUser != null -> selectedUser.displayName.ifBlank { "Tech" }
                    else -> "Selected tech"
                },
                color = HFColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(12.dp))
        }

        // Clocked-in banner sits separately so the period totals below
        // are always a clean 2x2 grid regardless of shift state.
        if (activeShift != null) {
            ClockedInBanner(shift = activeShift!!)
            Spacer(Modifier.size(12.dp))
        }

        PeriodTotalsGrid(totals = periods)

        // Request Time Off — visible to everyone. Submits a `pending`
        // row to hf_time_off_requests; admins approve later.
        Spacer(Modifier.size(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.04f))
                .border(1.dp, HFColors.StatusBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable { onRequestTimeOff() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Request Time Off",
                color = HFColors.StatusBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isAdmin) {
            Spacer(Modifier.size(10.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.06f))
                    .border(1.dp, HFColors.OnSurface.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                    .clickable {
                        val label = selectedUser?.displayName?.replace(" ", "_")?.lowercase()
                            ?: "all_shop"
                        com.hangarflow.app.data.TimeEntryExporter.export(
                            context, entries, label
                        )
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Export CSV",
                    color = HFColors.OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Admin-only queue of pending time-entry corrections filed by
        // techs. Admin reads the note, fixes the entry by hand (reject /
        // restore controls below), then marks the correction applied or
        // dismisses it. Mirrors the Desktop PendingApprovalsPanel.
        if (isAdmin) {
            CorrectionApprovalsSection(corrections = shopState.timeEntryCorrections)
        }

        // Reimbursement approvals — admins see a pending queue with
        // Approve/Deny; everyone sees their own submitted reimbursements.
        ReimbursementSection(
            reimbursements = shopState.reimbursements,
            isAdmin = isAdmin,
            myUserId = me?.id
        )

        if (isAdmin && anomalyFlags.isNotEmpty()) {
            Spacer(Modifier.size(16.dp))
            HoursAnomalyBanner(flags = anomalyFlags, entries = entries)
        }

        Spacer(Modifier.size(20.dp))

        // ---- History header (iOS: title + subtitle, Export on the right) ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "History",
                color = HFColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Admin already has a dedicated Export CSV button above; here we
            // mirror the iOS export affordance only for admins to avoid
            // orphaning a second action for techs (no behavior change).
        }
        Spacer(Modifier.size(2.dp))
        Text(
            "Your logged time, grouped by date.",
            color = HFColors.OnSurface.copy(alpha = 0.62f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.size(12.dp))

        // Segmented period control: Daily · Weekly · Bi-Weekly · Monthly · All
        HistorySegmentedControl(
            selected = historyPeriod,
            enabled = !historyUseRange,
            onSelect = { historyPeriod = it }
        )
        Spacer(Modifier.size(12.dp))

        // Custom date range toggle row (overrides the segments when on).
        CustomRangeRow(
            useRange = historyUseRange,
            weeksBack = rangeWeeksBack,
            onToggle = { historyUseRange = it },
            onWeeksBack = { rangeWeeksBack = it.coerceIn(1, 52) }
        )
        Spacer(Modifier.size(12.dp))

        if (entries.isEmpty()) {
            IOSPlaceholderPanel(message = "No time entries yet. Clock in from the home screen to start logging.")
            return
        }

        // TOTAL summary row for the selected period.
        if (historyEntries.isNotEmpty()) {
            HistoryTotalRow(minutes = historyTotalMinutes)
            Spacer(Modifier.size(14.dp))
        }

        // "Previous entries (n)" header with a count pill.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Previous entries",
                color = HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    historyEntries.size.toString(),
                    color = HFColors.OnSurface.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.size(10.dp))

        if (historyEntries.isEmpty()) {
            IOSPlaceholderPanel(message = "No previous entries for this period. Your earlier time entries appear here once logged on prior days.")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            allDays.forEach { day ->
                val dayEntries = grouped[day] ?: emptyList()
                if (dayEntries.isEmpty() && dayActivities[day] == null) return@forEach
                val dayMinutes = dayEntries.sumOf { it.minutesWorked }
                val activity = dayActivities[day]

                item(key = "group-$day") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Date header: bullet + uppercase date · day total.
                        DayHeader(day = day, minutes = dayMinutes)

                        // The day's entries grouped into one rounded card,
                        // hairline-separated — matches the iOS history card.
                        if (dayEntries.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(HFColors.OnSurface.copy(alpha = 0.05f))
                                    .border(
                                        1.dp,
                                        HFColors.OnSurface.copy(alpha = 0.08f),
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                dayEntries.forEachIndexed { index, entry ->
                                    EntryRow(
                                        entry = entry,
                                        isAdmin = isAdmin,
                                        isLast = index >= dayEntries.size - 1
                                    )
                                }
                            }
                        }

                        // Admin-only activity breakdown for this day.
                        if (isAdmin && activity != null) {
                            if (activity.completedWorkLogs.isNotEmpty()) {
                                ActivityLabel(
                                    "WORK COMPLETED · ${activity.completedWorkLogs.size}",
                                    HFColors.StatusGreen
                                )
                                activity.completedWorkLogs.forEach { wl -> ActivityWorkLogRow(wl) }
                            }
                            if (activity.filedSquawks.isNotEmpty()) {
                                ActivityLabel(
                                    "SQUAWKS FILED · ${activity.filedSquawks.size}",
                                    HFColors.StatusOrange
                                )
                                activity.filedSquawks.forEach { sq -> ActivitySquawkRow(sq) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeoplePicker(
    users: List<com.hangarflow.app.data.model.HFUserProfile>,
    currentUserId: String?,
    selectedUserId: String?,
    onSelect: (String?) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All Shop" leads so admins can eyeball total shop load fast.
        PersonChip(
            label = "All Shop",
            initials = "⌂",
            selected = selectedUserId == null,
            onClick = { onSelect(null) }
        )
        users.forEach { u ->
            val isMe = u.id == currentUserId
            PersonChip(
                label = if (isMe) "You" else u.displayName.ifBlank { "Tech" },
                initials = u.initials.ifBlank {
                    u.displayName.trim().split(" ").mapNotNull { it.firstOrNull()?.toString() }
                        .joinToString("").take(2).uppercase().ifBlank { "•" }
                },
                selected = selectedUserId == u.id,
                onClick = { onSelect(u.id) }
            )
        }
    }
}

@Composable
private fun PersonChip(
    label: String,
    initials: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.06f)
    val fg = if (selected) HFColors.BrandInk else HFColors.OnSurface
    val border = if (selected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (selected) HFColors.BrandInk.copy(alpha = 0.10f)
                    else HFColors.OnSurface.copy(alpha = 0.12f)
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(initials, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimeToggle(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val bg = when {
        !enabled -> HFColors.OnSurface.copy(alpha = 0.03f)
        isSelected -> HFColors.BrandWhite
        else -> HFColors.OnSurface.copy(alpha = 0.06f)
    }
    val fg = when {
        !enabled -> HFColors.OnSurface.copy(alpha = 0.25f)
        isSelected -> HFColors.BrandInk
        else -> HFColors.OnSurface
    }
    val border = when {
        !enabled -> HFColors.OnSurface.copy(alpha = 0.05f)
        isSelected -> HFColors.BrandWhite
        else -> HFColors.OnSurface.copy(alpha = 0.10f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ClockedInBanner(shift: com.hangarflow.app.data.ActiveShift) {
    val onLunch = shift.phase == com.hangarflow.app.data.ShiftPhase.OnLunch
    val accent = if (onLunch) HFColors.StatusOrange else HFColors.StatusGreen
    val label = if (onLunch) "ON LUNCH" else "CLOCKED IN"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.size(2.dp))
            Text(
                "Started ${shortTimeOf(shift.startedAt)}" +
                    if (onLunch) " · lunch started ${shortTimeOf(shift.lunchStartedAt!!)}" else "",
                color = HFColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            formatHours(shift.liveWorkedMinutes(Instant.now())),
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PeriodTotalsGrid(totals: PeriodTotals) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodTile(
                label = "TODAY",
                minutes = totals.today,
                accent = HFColors.StatusCyan,
                modifier = Modifier.weight(1f)
            )
            PeriodTile(
                label = "THIS WEEK",
                minutes = totals.week,
                accent = HFColors.StatusBlue,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodTile(
                label = "BI-WEEKLY",
                minutes = totals.biweekly,
                accent = HFColors.StatusPurple,
                modifier = Modifier.weight(1f)
            )
            PeriodTile(
                label = "THIS MONTH",
                minutes = totals.month,
                accent = HFColors.StatusGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PeriodTile(
    label: String,
    minutes: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            label,
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.size(4.dp))
        Text(
            formatHours(minutes),
            color = HFColors.OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DayHeader(day: String, minutes: Int) {
    // iOS: small dot · UPPERCASE DATE on the left, day total on the right.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(HFColors.OnSurface.copy(alpha = 0.35f))
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = prettyDay(day).uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatHours(minutes),
            color = HFColors.OnSurface.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EntryRow(entry: HFTimeEntry, isAdmin: Boolean, isLast: Boolean = true) {
    val isRejected = entry.approvalStatus == "rejected"
    // A single cell inside the day's rounded card (iOS history card style):
    // hairline-separated rows, no per-row border/background, hours rendered
    // in a rounded capsule on the right.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isRejected) Modifier.background(HFColors.StatusRed.copy(alpha = 0.05f))
                else Modifier
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.userName.ifBlank { "Unknown tech" },
                        color = HFColors.OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isRejected) {
                        Spacer(Modifier.size(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(HFColors.StatusRed.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("REJECTED", color = HFColors.StatusRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!entry.planeTailNumber.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(HFColors.OnSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            entry.planeTailNumber!!,
                            color = HFColors.OnSurface.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (entry.notes.isNotBlank()) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = entry.notes,
                        color = HFColors.OnSurface.copy(alpha = 0.66f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                }
                if (isRejected && !entry.rejectionReason.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "⚠ ${entry.rejectionReason}",
                        color = HFColors.StatusRed.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            // Hours capsule (iOS look).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        if (isRejected) HFColors.OnSurface.copy(alpha = 0.06f)
                        else HFColors.OnSurface.copy(alpha = 0.12f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = formatHours(entry.minutesWorked),
                    color = if (isRejected) HFColors.OnSurface.copy(alpha = 0.45f) else HFColors.OnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // Admin reject / restore controls (right-aligned capsule, iOS style).
        if (isAdmin) {
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (isRejected) {
                    ApprovalChip("↺ Restore", HFColors.StatusGreen) {
                        SharedStore.decideTimeEntry(entry.id, reject = false)
                    }
                } else {
                    ApprovalChip("✕ Reject", HFColors.StatusRed) {
                        SharedStore.decideTimeEntry(entry.id, reject = true)
                    }
                }
            }
        } else {
            // Tech-side affordance: file a correction request the admin can
            // approve / dismiss. The tech never edits the entry directly.
            var showCorrection by remember { mutableStateOf(false) }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ApprovalChip("✎ Request correction", HFColors.StatusBlue) {
                    showCorrection = true
                }
            }
            if (showCorrection) {
                RequestCorrectionDialog(
                    entry = entry,
                    onDismiss = { showCorrection = false }
                )
            }
        }
    }
    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(HFColors.OnSurface.copy(alpha = 0.07f))
        )
    }
}

@Composable
private fun ReimbursementSection(
    reimbursements: List<com.hangarflow.app.data.model.HFReimbursement>,
    isAdmin: Boolean,
    myUserId: String?
) {
    val visible = if (isAdmin) reimbursements else reimbursements.filter { it.userId == myUserId }
    if (visible.isEmpty()) return
    val pending = visible.filter { it.status == "pending" }
    val decided = visible.filter { it.status != "pending" }.take(6)

    Spacer(Modifier.size(18.dp))
    Text(
        if (isAdmin) "REIMBURSEMENTS" else "MY REIMBURSEMENTS",
        color = HFColors.OnSurface.copy(alpha = 0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
    Spacer(Modifier.size(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pending.forEach { r -> ReimbursementRow(r, showActions = isAdmin) }
        decided.forEach { r -> ReimbursementRow(r, showActions = false) }
    }
}

@Composable
private fun ReimbursementRow(r: com.hangarflow.app.data.model.HFReimbursement, showActions: Boolean) {
    val statusColor = when (r.status) {
        "approved" -> HFColors.StatusGreen
        "denied" -> HFColors.StatusRed
        else -> HFColors.StatusOrange
    }
    val amount = "$%.2f".format(r.amountCents / 100.0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.userName.ifBlank { "Tech" }, color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (r.description.isNotBlank()) {
                    Text(r.description, color = HFColors.OnSurface.copy(alpha = 0.60f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (!showActions) {
                    Text(
                        r.status.replaceFirstChar { it.uppercase() },
                        color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (showActions) {
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApprovalChip("Approve", HFColors.StatusGreen, Modifier.weight(1f)) {
                    SharedStore.decideReimbursement(r.id, approve = true)
                }
                ApprovalChip("Deny", HFColors.StatusRed, Modifier.weight(1f)) {
                    SharedStore.decideReimbursement(r.id, approve = false)
                }
            }
        }
    }
}

@Composable
private fun ApprovalChip(label: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- time-entry corrections ----------

/**
 * Admin queue of pending time-entry corrections. Renders nothing when
 * there are none. Each row shows who filed it + the requested change,
 * with "Mark applied" / "Dismiss" actions. Mirrors the Desktop
 * PendingApprovalsPanel correction list.
 */
@Composable
private fun CorrectionApprovalsSection(
    corrections: List<com.hangarflow.app.data.model.HFTimeEntryCorrection>
) {
    val pending = corrections.filter { it.status == "pending" }
    if (pending.isEmpty()) return

    Spacer(Modifier.size(18.dp))
    Text(
        "TIME-ENTRY CORRECTIONS",
        color = HFColors.OnSurface.copy(alpha = 0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
    Spacer(Modifier.size(10.dp))
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pending.forEach { c ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.04f))
                    .border(1.dp, HFColors.StatusBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    c.userName.ifBlank { "Tech" },
                    color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                if (c.requestedChange.isNotBlank()) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        c.requestedChange,
                        color = HFColors.OnSurface.copy(alpha = 0.66f),
                        fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 4
                    )
                }
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApprovalChip("Mark applied", HFColors.StatusGreen, Modifier.weight(1f)) {
                        scope.launch { SharedStore.decideTimeEntryCorrection(c.id, applied = true) }
                    }
                    ApprovalChip("Dismiss", HFColors.StatusRed, Modifier.weight(1f)) {
                        scope.launch { SharedStore.decideTimeEntryCorrection(c.id, applied = false) }
                    }
                }
            }
        }
    }
}

/** Tech-side dialog to file a correction note against one time entry. */
@Composable
private fun RequestCorrectionDialog(entry: HFTimeEntry, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        containerColor = HFColors.Background,
        titleContentColor = HFColors.OnSurface,
        textContentColor = HFColors.OnSurface,
        title = { Text("Request correction", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Describe what's wrong with this entry — the admin fixes it for you.",
                    color = HFColors.OnSurface.copy(alpha = 0.66f), fontSize = 12.sp
                )
                if (entry.entryDate.isNotBlank() || entry.minutesWorked > 0) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "${entry.entryDate.take(10)} · ${formatHours(entry.minutesWorked)}",
                        color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Should be 8h not 6h — forgot to clock back in", color = HFColors.OnSurface.copy(alpha = 0.4f)) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                        unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                        focusedBorderColor = HFColors.StatusBlue.copy(alpha = 0.55f),
                        unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
                        focusedTextColor = HFColors.OnSurface,
                        unfocusedTextColor = HFColors.OnSurface,
                        cursorColor = HFColors.OnSurface
                    )
                )
                error?.let {
                    Spacer(Modifier.size(6.dp))
                    Text(it, color = HFColors.StatusRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && note.isNotBlank(),
                onClick = {
                    submitting = true
                    scope.launch {
                        when (val r = SharedStore.submitTimeEntryCorrection(entry.id, note)) {
                            is SharedStore.CreateResult.Success -> onDismiss()
                            is SharedStore.CreateResult.Error -> {
                                error = r.message
                                submitting = false
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (submitting) "Sending…" else "Send",
                    color = HFColors.StatusBlue, fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text("Cancel", color = HFColors.OnSurface.copy(alpha = 0.7f))
            }
        }
    )
}

// ---------- History view (mirrors iOS IOSTimeCardHubView History) ----------

private enum class HistoryPeriod(val label: String) {
    Daily("Daily"),
    Weekly("Weekly"),
    BiWeekly("Bi-Weekly"),
    Monthly("Monthly"),
    All("All")
}

/**
 * Filter the (already user-scoped) entries down to what History should show.
 * Pure view-side filtering — never mutates data. Mirrors iOS `previousEntries`:
 * Daily = today only; the dated presets look back N days; All = everything;
 * Custom range looks back `weeksBack` whole weeks from today.
 */
private fun filterHistory(
    entries: List<HFTimeEntry>,
    period: HistoryPeriod,
    useRange: Boolean,
    weeksBack: Int
): List<HFTimeEntry> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    fun dateOf(e: HFTimeEntry): LocalDate? =
        runCatching { LocalDate.parse(e.entryDate.take(10)) }.getOrNull()

    if (useRange) {
        val start = today.minusWeeks(weeksBack.toLong())
        return entries.filter { e ->
            val d = dateOf(e) ?: return@filter false
            !d.isBefore(start) && !d.isAfter(today)
        }
    }

    val cutoff: LocalDate? = when (period) {
        HistoryPeriod.Daily -> today
        HistoryPeriod.Weekly -> today.minusDays(7)
        HistoryPeriod.BiWeekly -> today.minusDays(14)
        HistoryPeriod.Monthly -> today.minusMonths(1)
        HistoryPeriod.All -> null
    }
    return entries.filter { e ->
        val d = dateOf(e) ?: return@filter false
        when (period) {
            // Daily shows just today; presets exclude today (it lives in the
            // live totals grid above), matching iOS.
            HistoryPeriod.Daily -> d == today
            HistoryPeriod.All -> true
            else -> d != today && (cutoff == null || !d.isBefore(cutoff))
        }
    }
}

@Composable
private fun HistorySegmentedControl(
    selected: HistoryPeriod,
    enabled: Boolean,
    onSelect: (HistoryPeriod) -> Unit
) {
    // iOS segmented control: a single rounded track; selected = white pill,
    // others muted. Equal-width segments.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        HistoryPeriod.values().forEach { p ->
            val isSel = p == selected
            val bg = when {
                !enabled -> if (isSel) HFColors.OnSurface.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent
                isSel -> HFColors.BrandWhite
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val fg = when {
                !enabled -> HFColors.OnSurface.copy(alpha = 0.30f)
                isSel -> HFColors.BrandInk
                else -> HFColors.OnSurface.copy(alpha = 0.70f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable(enabled = enabled) { onSelect(p) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.label,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CustomRangeRow(
    useRange: Boolean,
    weeksBack: Int,
    onToggle: (Boolean) -> Unit,
    onWeeksBack: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Custom date range",
                color = HFColors.OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // Lightweight pill toggle (no Material Switch, to match the
            // iOS-flavored visuals and stay self-contained).
            val trackOn = HFColors.StatusCyan
            val track = if (useRange) trackOn else HFColors.OnSurface.copy(alpha = 0.18f)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(track)
                    .clickable { onToggle(!useRange) }
                    .padding(3.dp)
                    .size(width = 42.dp, height = 24.dp),
                contentAlignment = if (useRange) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(HFColors.BrandWhite)
                )
            }
        }
        if (useRange) {
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last $weeksBack ${if (weeksBack == 1) "week" else "weeks"}",
                    color = HFColors.OnSurface.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                StepperChip("−") { onWeeksBack(weeksBack - 1) }
                Spacer(Modifier.size(8.dp))
                StepperChip("+") { onWeeksBack(weeksBack + 1) }
            }
        }
    }
}

@Composable
private fun StepperChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.10f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryTotalRow(minutes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TOTAL",
            color = HFColors.OnSurface.copy(alpha = 0.50f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatHours(minutes),
            color = HFColors.StatusCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------- helpers ----------

private data class PeriodTotals(
    val today: Int,
    val week: Int,
    val biweekly: Int,
    val month: Int
)

/**
 * Bucket time entries into day / week / bi-weekly / month totals.
 * Week anchors to Monday (matches how shop pay periods are cut). The
 * bi-weekly bucket covers the current pay week + the one before it.
 */
private fun computePeriodTotals(entries: List<HFTimeEntry>): PeriodTotals {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val biweeklyStart = weekStart.minusWeeks(1)
    val monthStart = today.withDayOfMonth(1)

    var dayMin = 0
    var weekMin = 0
    var biweeklyMin = 0
    var monthMin = 0
    for (e in entries) {
        val d = runCatching { LocalDate.parse(e.entryDate.take(10)) }.getOrNull() ?: continue
        if (d == today) dayMin += e.minutesWorked
        if (!d.isBefore(weekStart) && !d.isAfter(today)) weekMin += e.minutesWorked
        if (!d.isBefore(biweeklyStart) && !d.isAfter(today)) biweeklyMin += e.minutesWorked
        if (!d.isBefore(monthStart) && !d.isAfter(today)) monthMin += e.minutesWorked
    }
    return PeriodTotals(dayMin, weekMin, biweeklyMin, monthMin)
}

// ---------- admin activity rows ----------

@Composable
private fun ActivityLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun ActivityWorkLogRow(wl: com.hangarflow.app.data.model.HFWorkLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.StatusGreen.copy(alpha = 0.06f))
            .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                wl.title.ifBlank { "Untitled work log" },
                color = HFColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            val meta = listOfNotNull(
                wl.planeTailNumber.takeIf { it.isNotBlank() },
                com.hangarflow.app.data.model.HFWorkCategory.fromRaw(wl.category).label,
                if (wl.loggedMinutes > 0) formatHours(wl.loggedMinutes) else null
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(HFColors.StatusGreen.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                "Done",
                color = HFColors.StatusGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActivitySquawkRow(sq: com.hangarflow.app.data.model.HFSquawk) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.StatusOrange.copy(alpha = 0.06f))
            .border(1.dp, HFColors.StatusOrange.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                sq.title.ifBlank { "Untitled squawk" },
                color = HFColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            val meta = listOfNotNull(
                sq.planeTailNumber.takeIf { it.isNotBlank() },
                sq.reportedByUserName?.takeIf { it.isNotBlank() }?.let { "by $it" }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(HFColors.StatusOrange.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                sq.status.replaceFirstChar { it.uppercase() },
                color = HFColors.StatusOrange,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------- helpers ----------

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

private fun prettyDay(iso: String): String = runCatching {
    val date = LocalDate.parse(iso)
    val today = LocalDate.now(ZoneId.systemDefault())
    when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}.getOrDefault(iso)

private fun shortTimeOf(instant: Instant): String = runCatching {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault("")

@Composable
private fun HoursAnomalyBanner(flags: List<HFCloudSyncService.HoursFlag>, entries: List<HFTimeEntry>) {
    val byId = remember(entries) { entries.associateBy { it.id } }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.StatusOrange.copy(alpha = 0.08f))
            .border(1.dp, HFColors.StatusOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "⚠️ ${flags.size} ${if (flags.size == 1) "entry" else "entries"} to double-check",
            color = HFColors.StatusOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        flags.take(8).forEach { f ->
            val e = byId[f.id]
            val who = e?.userName?.takeIf { it.isNotBlank() } ?: "Entry"
            val date = e?.entryDate?.take(10) ?: ""
            val c = if (f.severity == "warn") HFColors.StatusRed else HFColors.OnSurface.copy(alpha = 0.75f)
            Text("• $who${if (date.isNotBlank()) " ($date)" else ""}: ${f.reason}", color = c, fontSize = 11.sp)
        }
        Text("AI flags — verify before approving.", color = HFColors.OnSurface.copy(alpha = 0.4f), fontSize = 9.sp)
    }
}
