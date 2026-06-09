package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.ui.theme.HFColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Schedule hub — monthly calendar showing plane arrivals (drop-offs)
 * and RTS deadlines, plus a top-level "Request Time Off" entry point.
 * Mirrors iOS IOSScheduleHubView v1 scope (calendar events / crew
 * rotation are queued as part of task #41 cross-platform).
 */
@Composable
fun ScheduleHub() {
    var showRequestTimeOff by remember { mutableStateOf(false) }
    var addEventDate by remember { mutableStateOf<LocalDate?>(null) }
    if (showRequestTimeOff) {
        RequestTimeOffSheet(onDismiss = { showRequestTimeOff = false })
        return
    }
    addEventDate?.let { date ->
        AddCalendarEventSheet(
            initialDate = date,
            onDismiss = { addEventDate = null }
        )
    }
    ScheduleHubContent(
        onRequestTimeOff = { showRequestTimeOff = true },
        onAddEvent = { addEventDate = it }
    )
}

@Composable
private fun ScheduleHubContent(onRequestTimeOff: () -> Unit, onAddEvent: (LocalDate) -> Unit) {
    val shopState by SharedStore.state.collectAsState()
    val authState by com.hangarflow.app.auth.AuthManager.state.collectAsState()
    val isAdmin = authState.isAdmin
    // Admins and lead techs can drop calendar events / reminders.
    val canManage = authState.isAdmin || authState.isLeadTech
    val scope = rememberCoroutineScope()

    val today = LocalDate.now()
    var monthAnchor by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(today) }

    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val dayLongFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy") }

    // Parse plane arrival/deadline dates once per plane list change.
    val planeEvents = remember(shopState.planes) {
        buildPlaneEventIndex(shopState.planes)
    }

    // Calendar events visible to this user, expanded into a per-day index.
    val myUserId = shopState.currentUser?.id
    val calendarByDay = remember(shopState.calendarEvents, isAdmin, myUserId) {
        buildCalendarEventIndex(shopState.calendarEvents, isAdmin, myUserId)
    }
    val eventDayKeys = remember(planeEvents, calendarByDay) {
        planeEvents.keys + calendarByDay.keys
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Schedule", color = HFColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Plane arrivals, RTS deadlines, and time-off.",
            color = HFColors.OnSurface.copy(alpha = 0.68f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(14.dp))
        StatsStrip(month = monthAnchor, plane = shopState.planes)

        Spacer(Modifier.height(14.dp))
        // Top actions: every tech can request PTO; admins can also drop a
        // calendar event on the selected day right from the calendar.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.04f))
                    .border(1.dp, HFColors.StatusBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { onRequestTimeOff() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isAdmin) "Mark Time Off" else "Request Time Off",
                    color = HFColors.StatusBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (canManage) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.04f))
                        .border(1.dp, HFColors.StatusYellow.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                        .clickable { onAddEvent(selectedDay ?: today) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Add Event",
                        color = HFColors.StatusYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        MonthNav(
            monthLabel = monthAnchor.format(monthFormatter),
            onPrev = { monthAnchor = monthAnchor.minusMonths(1) },
            onNext = { monthAnchor = monthAnchor.plusMonths(1) }
        )

        Spacer(Modifier.height(10.dp))
        CalendarGrid(
            month = monthAnchor,
            today = today,
            selectedDay = selectedDay,
            eventDays = eventDayKeys,
            onSelect = { selectedDay = it }
        )

        selectedDay?.let { day ->
            Spacer(Modifier.height(18.dp))
            Text(
                day.format(dayLongFormatter),
                color = HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            val events = planeEvents[day].orEmpty()
            val dayCalendarEvents = calendarByDay[day].orEmpty()
            if (events.isEmpty() && dayCalendarEvents.isEmpty()) {
                Text(
                    "Nothing on this day.",
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayCalendarEvents.forEach { ev ->
                        CalendarEventRow(
                            event = ev,
                            canDelete = isAdmin,
                            scope = scope
                        )
                    }
                    events.forEach { event ->
                        EventRow(event)
                    }
                }
            }
        }

        TimeOffSection(
            requests = shopState.timeOffRequests,
            isAdmin = isAdmin,
            myUserId = shopState.currentUser?.id
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Time-off requests. Admins see a pending-approval queue (Approve/Deny)
 * plus recently decided rows; everyone else sees their own requests with
 * current status. Mirrors the iOS schedule hub's PTO surface.
 */
@Composable
private fun TimeOffSection(
    requests: List<com.hangarflow.app.data.model.HFTimeOffRequest>,
    isAdmin: Boolean,
    myUserId: String?
) {
    if (requests.isEmpty()) return
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
    val pending = requests.filter { it.status == "pending" }
    val mine = requests.filter { it.userId == myUserId }
    val decided = requests.filter { it.status != "pending" }.take(8)

    Spacer(Modifier.height(22.dp))
    if (isAdmin && pending.isNotEmpty()) {
        Text(
            "PENDING TIME-OFF (${pending.size})",
            color = HFColors.StatusOrange, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pending.forEach { req -> TimeOffRow(req, dateFmt, showActions = true) }
        }
    }

    val historyRows = if (isAdmin) decided else mine
    if (historyRows.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            if (isAdmin) "RECENT DECISIONS" else "MY TIME-OFF",
            color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            historyRows.forEach { req -> TimeOffRow(req, dateFmt, showActions = false) }
        }
    }
}

@Composable
private fun TimeOffRow(
    req: com.hangarflow.app.data.model.HFTimeOffRequest,
    dateFmt: DateTimeFormatter,
    showActions: Boolean
) {
    val statusColor = when (req.status) {
        "approved" -> HFColors.StatusGreen
        "denied" -> HFColors.StatusRed
        else -> HFColors.StatusOrange
    }
    val range = remember(req.startDate, req.endDate) {
        val s = parseDate(req.startDate)
        val e = parseDate(req.endDate)
        when {
            s != null && e != null && s == e -> s.format(dateFmt)
            s != null && e != null -> "${s.format(dateFmt)} – ${e.format(dateFmt)}"
            else -> "${req.startDate} – ${req.endDate}"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(req.userName, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(range, color = HFColors.OnSurface.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (req.reason.isNotBlank()) {
                    Text(req.reason, color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 11.sp)
                }
            }
            if (!showActions) {
                Text(
                    req.status.replaceFirstChar { it.uppercase() },
                    color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        if (showActions) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecisionButton("Approve", HFColors.StatusGreen, Modifier.weight(1f)) {
                    SharedStore.decideTimeOffRequest(req.id, approve = true)
                }
                DecisionButton("Deny", HFColors.StatusRed, Modifier.weight(1f)) {
                    SharedStore.decideTimeOffRequest(req.id, approve = false)
                }
            }
        }
    }
}

@Composable
private fun DecisionButton(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatsStrip(month: YearMonth, plane: List<HFPlane>) {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val dropOffs = plane.mapNotNull { parseDate(it.arrivalDate) }
        .count { !it.isBefore(first) && !it.isAfter(last) }
    val rtsDue = plane.mapNotNull { parseDate(it.deadlineDate) }
        .count { !it.isBefore(first) && !it.isAfter(last) }
    val today = LocalDate.now()
    val onLine = plane.count { p ->
        val dl = parseDate(p.deadlineDate)
        dl == null || !dl.isBefore(today)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(label = "DROP-OFFS", value = dropOffs.toString(), accent = HFColors.StatusBlue, modifier = Modifier.weight(1f))
        StatTile(label = "RTS DEADLINES", value = rtsDue.toString(), accent = HFColors.StatusOrange, modifier = Modifier.weight(1f))
        StatTile(label = "ON THE LINE", value = onLine.toString(), accent = HFColors.StatusGreen, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(value, color = HFColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun MonthNav(monthLabel: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(HFColors.OnSurface.copy(alpha = 0.10f))
                .clickable { onPrev() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous month", tint = HFColors.OnSurface, modifier = Modifier.size(18.dp))
        }
        Text(monthLabel, color = HFColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(HFColors.OnSurface.copy(alpha = 0.10f))
                .clickable { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next month", tint = HFColors.OnSurface, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDay: LocalDate?,
    eventDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit
) {
    // Weekday header (Sun-first, matching iOS calendar)
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayLabel ->
            Text(
                dayLabel,
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    val firstOfMonth = month.atDay(1)
    // Sunday-leading offset: DayOfWeek.SUNDAY = 7 (ISO), so we compute
    // (dow % 7) to get 0..6 with Sunday = 0.
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7

    // 6 rows max, 7 cols. Build a flat list and chunk into rows of 7.
    val cells: List<LocalDate?> = (0 until totalCells).map { idx ->
        val dayOfMonth = idx - leadingBlanks + 1
        if (dayOfMonth in 1..daysInMonth) month.atDay(dayOfMonth) else null
    }
    cells.chunked(7).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { date ->
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (date != null) {
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDay,
                            hasEvents = date in eventDays,
                            onClick = { onSelect(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, hasEvents: Boolean, onClick: () -> Unit) {
    val bg = when {
        isSelected -> HFColors.StatusBlue.copy(alpha = 0.30f)
        isToday -> HFColors.OnSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val border = when {
        isSelected -> HFColors.StatusBlue
        isToday -> HFColors.StatusBlue.copy(alpha = 0.50f)
        else -> HFColors.OnSurface.copy(alpha = 0.08f)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = HFColors.OnSurface,
            fontSize = 13.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
        )
        if (hasEvents) {
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(HFColors.StatusOrange)
            )
        }
    }
}

@Composable
private fun EventRow(event: PlaneScheduleEvent) {
    val accent = when (event.kind) {
        PlaneEventKind.Arrival -> HFColors.StatusBlue
        PlaneEventKind.Deadline -> HFColors.StatusOrange
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                color = HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                event.subtitle,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ----- Helpers -----

private enum class PlaneEventKind { Arrival, Deadline }

private data class PlaneScheduleEvent(
    val kind: PlaneEventKind,
    val title: String,
    val subtitle: String
)

private fun buildPlaneEventIndex(planes: List<HFPlane>): Map<LocalDate, List<PlaneScheduleEvent>> {
    val out = mutableMapOf<LocalDate, MutableList<PlaneScheduleEvent>>()
    planes.forEach { plane ->
        parseDate(plane.arrivalDate)?.let { d ->
            // Receiving inspection: append what the plane is coming in for.
            val insp = plane.incomingInspection?.takeIf { it.isNotBlank() }
            out.getOrPut(d) { mutableListOf() }
                .add(PlaneScheduleEvent(
                    kind = PlaneEventKind.Arrival,
                    title = "${plane.tailNumber} arrives",
                    subtitle = if (insp != null) "Drop-off · $insp" else "Drop-off"
                ))
        }
        parseDate(plane.deadlineDate)?.let { d ->
            out.getOrPut(d) { mutableListOf() }
                .add(PlaneScheduleEvent(
                    kind = PlaneEventKind.Deadline,
                    title = "${plane.tailNumber} RTS deadline",
                    subtitle = "Return to service"
                ))
        }
    }
    return out
}

private fun parseDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        // Accept full ISO instant or date-only
        if (iso.length >= 10) LocalDate.parse(iso.substring(0, 10)) else LocalDate.parse(iso)
    }.getOrNull()
}

// ----- Calendar events -----

/** Expand each visible calendar event across its inclusive day range,
 *  applying the visibility test (public/admin_only/personal). */
private fun buildCalendarEventIndex(
    events: List<com.hangarflow.app.data.model.HFCalendarEvent>,
    isAdmin: Boolean,
    myUserId: String?
): Map<LocalDate, List<com.hangarflow.app.data.model.HFCalendarEvent>> {
    val out = mutableMapOf<LocalDate, MutableList<com.hangarflow.app.data.model.HFCalendarEvent>>()
    events.forEach { ev ->
        val visible = when (ev.visibility) {
            "admin_only" -> isAdmin
            "personal" -> ev.createdByUserId != null && ev.createdByUserId == myUserId
            else -> true
        }
        if (!visible) return@forEach
        val start = parseDate(ev.startDate) ?: return@forEach
        val end = parseDate(ev.endDate) ?: start
        var d = start
        var guard = 0
        while (!d.isAfter(end) && guard < 366) {
            out.getOrPut(d) { mutableListOf() }.add(ev)
            d = d.plusDays(1)
            guard++
        }
    }
    return out
}

@Composable
private fun CalendarEventRow(
    event: com.hangarflow.app.data.model.HFCalendarEvent,
    canDelete: Boolean,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val accent = HFColors.StatusYellow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(
                event.planeTailNumber?.takeIf { it.isNotBlank() } ?: "Org-wide",
                event.description.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            }
            if (event.visibility != "public") {
                Text(
                    if (event.visibility == "admin_only") "Admins only" else "Personal",
                    color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium
                )
            }
        }
        if (canDelete) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HFColors.StatusRed.copy(alpha = 0.12f))
                    .clickable { scope.launch { SharedStore.deleteCalendarEvent(event.id) } }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Delete", color = HFColors.StatusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Admin sheet to add a calendar event. Title + dates required; plane
 * scope and audience optional. Date steppers (no native picker dep).
 */
@Composable
private fun AddCalendarEventSheet(
    initialDate: LocalDate,
    onDismiss: () -> Unit
) {
    val shopState by SharedStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(initialDate) }
    var endDate by remember { mutableStateOf(initialDate) }
    var planeTail by remember { mutableStateOf<String?>(null) }
    var visibility by remember { mutableStateOf("public") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Optional reminder: fire a push to the chosen teammate at a chosen
    // date + hour. Defaults to "remind me" on the event's start date at 8am.
    var remindOn by remember { mutableStateOf(false) }
    var remindDate by remember { mutableStateOf(initialDate) }
    var remindHour by remember { mutableStateOf(8) }
    var remindUserAuthId by remember { mutableStateOf(shopState.currentUser?.authUserId) }

    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    val canSave = title.trim().isNotEmpty() && !saving

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add to schedule", color = HFColors.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Visible to the org. Use for inspection windows, meetings, training days, etc.",
                    color = HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 11.sp
                )
                EventField("TITLE", title, "Borescope inspection, team meeting…") { title = it }
                EventField("DESCRIPTION (OPTIONAL)", description, "Context techs should see.", singleLine = false) { description = it }

                Text("START DATE", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                DateStepperRow(startDate, dateFmt) {
                    startDate = it
                    if (endDate.isBefore(it)) endDate = it
                }
                Text("END DATE", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                DateStepperRow(endDate, dateFmt) {
                    endDate = if (it.isBefore(startDate)) startDate else it
                }

                Text("PLANE (OPTIONAL)", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScopeChip("Org-wide", planeTail == null) { planeTail = null }
                    shopState.planes.forEach { plane ->
                        ScopeChip(plane.tailNumber, planeTail == plane.tailNumber) { planeTail = plane.tailNumber }
                    }
                }

                Text("WHO CAN SEE IT", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScopeChip("Everyone", visibility == "public") { visibility = "public" }
                    ScopeChip("Admins", visibility == "admin_only") { visibility = "admin_only" }
                    ScopeChip("Only me", visibility == "personal") { visibility = "personal" }
                }

                androidx.compose.material3.HorizontalDivider(color = HFColors.OnSurface.copy(alpha = 0.10f))

                // Reminder: notify a teammate at a date + hour.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SEND A REMINDER", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Push a notification when it's due.", color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    androidx.compose.material3.Switch(checked = remindOn, onCheckedChange = { remindOn = it })
                }
                if (remindOn) {
                    Text("REMIND WHO", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val meId = shopState.currentUser?.authUserId
                        shopState.users.filter { it.isActive && !it.authUserId.isNullOrBlank() }.forEach { u ->
                            val label = if (u.authUserId == meId) "${u.displayName} (me)" else u.displayName
                            ScopeChip(label.ifBlank { "Teammate" }, remindUserAuthId == u.authUserId) { remindUserAuthId = u.authUserId }
                        }
                    }
                    Text("REMIND ON", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    DateStepperRow(remindDate, dateFmt) { remindDate = it }
                    Text("AT", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StepChip("◄") { remindHour = (remindHour + 23) % 24 }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(formatHour(remindHour), color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        StepChip("►") { remindHour = (remindHour + 1) % 24 }
                    }
                }

                error?.let { Text(it, color = HFColors.StatusRed, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = canSave,
                onClick = {
                    saving = true; error = null
                    val tail = planeTail
                    val planeId = tail?.let { t -> shopState.planes.firstOrNull { it.tailNumber == t }?.id }
                    val remindAtIso = if (remindOn && !remindUserAuthId.isNullOrBlank()) {
                        remindDate.atTime(remindHour, 0)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toString()
                    } else null
                    scope.launch {
                        when (val r = SharedStore.createCalendarEvent(
                            title = title,
                            description = description,
                            startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            planeId = planeId,
                            planeTailNumber = tail,
                            colorHex = null,
                            visibility = visibility,
                            remindAt = remindAtIso,
                            remindUserId = if (remindAtIso != null) remindUserAuthId else null
                        )) {
                            SharedStore.CreateResult.Success -> onDismiss()
                            is SharedStore.CreateResult.Error -> { error = r.message; saving = false }
                        }
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save", color = HFColors.StatusYellow, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") } }
    )
}

/** 24h hour int → friendly "8:00 AM" / "1:00 PM". */
private fun formatHour(h: Int): String {
    val hour12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    val ampm = if (h < 12) "AM" else "PM"
    return "$hour12:00 $ampm"
}

@Composable
private fun EventField(label: String, value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    Column {
        Text(label, color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = HFColors.OnSurface.copy(alpha = 0.4f), fontSize = 13.sp) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = HFColors.OnSurface, fontSize = 13.sp)
        )
    }
}

@Composable
private fun DateStepperRow(date: LocalDate, fmt: DateTimeFormatter, onChange: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepChip("◄") { onChange(date.minusDays(1)) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.06f))
                .border(1.dp, HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(date.format(fmt), color = HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        StepChip("►") { onChange(date.plusDays(1)) }
        StepChip("+7") { onChange(date.plusDays(7)) }
    }
}

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = HFColors.StatusYellow
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) accent else HFColors.OnSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
