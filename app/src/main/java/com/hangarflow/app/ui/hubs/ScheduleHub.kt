package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/**
 * Schedule hub — monthly calendar showing plane arrivals (drop-offs)
 * and RTS deadlines, plus a top-level "Request Time Off" entry point.
 * Mirrors iOS IOSScheduleHubView v1 scope (calendar events / crew
 * rotation are queued as part of task #41 cross-platform).
 */
@Composable
fun ScheduleHub() {
    var showRequestTimeOff by remember { mutableStateOf(false) }
    if (showRequestTimeOff) {
        RequestTimeOffSheet(onDismiss = { showRequestTimeOff = false })
        return
    }
    ScheduleHubContent(onRequestTimeOff = { showRequestTimeOff = true })
}

@Composable
private fun ScheduleHubContent(onRequestTimeOff: () -> Unit) {
    val shopState by SharedStore.state.collectAsState()
    val authState by com.hangarflow.app.auth.AuthManager.state.collectAsState()
    val isAdmin = authState.isAdmin

    val today = LocalDate.now()
    var monthAnchor by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(today) }

    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val dayLongFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy") }

    // Parse plane arrival/deadline dates once per plane list change.
    val planeEvents = remember(shopState.planes) {
        buildPlaneEventIndex(shopState.planes)
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
        // Top action: every tech can request PTO right from the calendar.
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
                if (isAdmin) "Mark Time Off" else "Request Time Off",
                color = HFColors.StatusBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
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
            eventDays = planeEvents.keys,
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
            if (events.isEmpty()) {
                Text(
                    "Nothing on this day.",
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        EventRow(event)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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
