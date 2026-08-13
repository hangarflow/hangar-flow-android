package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFTimeEntry
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A tech's own timesheet: the segments they worked, what they did, and
 * which aircraft it was against.
 *
 * Replaces clock in / clock out. A running clock and hand-entered
 * segments are two records of the same hours, and when they disagree —
 * a forgotten punch, a shift over midnight — nothing says which one
 * payroll should trust. Segments are what the office reviews, so they win.
 *
 * Everything files pending. Nothing is payable until an admin approves.
 */
@Composable
fun MyTimeHub() {
    val state by SharedStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf(LocalDate.now()) }
    var adding by remember { mutableStateOf(false) }

    val me = state.currentUser
    val mine = remember(state.timeEntries, day, me?.id) {
        state.timeEntries.filter { e ->
            val d = runCatching { LocalDate.parse((e.startedAt ?: e.entryDate).take(10)) }.getOrNull()
            d == day && (e.userId == me?.authUserId || e.userId == me?.id ||
                (e.userId == null && e.userName.equals(me?.displayName ?: "", ignoreCase = true)))
        }.sortedBy { it.startedAt ?: it.entryDate }
    }
    val total = mine.sumOf { it.minutesWorked }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My Time", color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(day.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 11.sp)
            }
            Step("‹") { day = day.minusDays(1) }
            Spacer(Modifier.width(6.dp))
            // No booking time you have not worked yet.
            if (day.isBefore(LocalDate.now())) Step("›") { day = day.plusDays(1) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hrs(total), color = HFColors.StatusGreen, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("logged", color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(100.dp))
                    .background(HFColors.StatusGreen.copy(alpha = 0.15f))
                    .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                    .clickable { adding = true }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) { Text("+ Add time", color = HFColors.StatusGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        if (mine.isEmpty()) {
            Text("Nothing logged for this day yet.",
                color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 13.sp)
        } else {
            mine.forEach { SegRow(it) }
        }
    }

    if (adding) {
        AddSegmentSheet(
            day = day,
            onDismiss = { adding = false },
            onSave = { s, e, pid, tail, notes ->
                scope.launch {
                    if (SharedStore.addTimeSegment(s, e, pid, tail, notes)) adding = false
                }
            }
        )
    }
}

@Composable
private fun SegRow(e: HFTimeEntry) {
    val (label, color) = when (e.approvalStatus) {
        "approved" -> "Approved" to HFColors.StatusGreen
        "rejected" -> "Rejected" to HFColors.StatusRed
        else -> "Pending" to HFColors.StatusOrange
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(span(e), color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(hrs(e.minutesWorked), color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(100.dp)).background(color.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
        e.planeTailNumber?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = HFColors.StatusBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (e.notes.isNotBlank()) {
            Text(e.notes, color = HFColors.OnSurface.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        if (e.approvalStatus == "rejected" && !e.rejectionReason.isNullOrBlank()) {
            Text("Reason: ${e.rejectionReason}", color = HFColors.StatusRed, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSegmentSheet(
    day: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String?, String) -> Unit
) {
    val state by SharedStore.state.collectAsState()
    var from by remember { mutableStateOf("8:00 AM") }
    var to by remember { mutableStateOf("12:00 PM") }
    var tail by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val s = parseTimeLoose(from)
    val e = parseTimeLoose(to)
    val minutes = if (s != null && e != null && e.isAfter(s))
        java.time.Duration.between(s, e).toMinutes().toInt() else null

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = HFColors.Surface,
        contentColor = HFColors.OnSurface
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add time", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HFColors.OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("From") },
                    singleLine = true, isError = s == null, modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text("To") },
                    singleLine = true, isError = e == null, modifier = Modifier.weight(1f))
            }
            Text(
                when {
                    s == null || e == null -> "Use a time like 8:00 AM, 08:00 or 1630."
                    minutes == null -> "The end has to be after the start."
                    else -> "That's ${hrs(minutes)}."
                },
                color = if (minutes == null) HFColors.StatusOrange else HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
            Text("AIRCRAFT (OPTIONAL)", color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pick("None", tail == null) { tail = null }
                state.planes.forEach { p -> Pick(p.tailNumber, tail == p.tailNumber) { tail = p.tailNumber } }
            }
            OutlinedTextField(notes, { notes = it },
                label = { Text("What did you work on?") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(
                        if (minutes != null && !busy) HFColors.StatusGreen.copy(alpha = 0.18f)
                        else HFColors.OnSurface.copy(alpha = 0.06f)
                    )
                    .clickable(enabled = minutes != null && !busy) {
                        busy = true
                        val z = ZoneId.systemDefault()
                        onSave(
                            day.atTime(s!!).atZone(z).toInstant().toString(),
                            day.atTime(e!!).atZone(z).toInstant().toString(),
                            state.planes.firstOrNull { it.tailNumber == tail }?.id,
                            tail, notes
                        )
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (busy) "Saving…" else "Save",
                    color = if (minutes != null) HFColors.StatusGreen else HFColors.OnSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Pick(label: String, sel: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(100.dp))
            .background(if (sel) HFColors.StatusBlue.copy(alpha = 0.15f) else HFColors.OnSurface.copy(alpha = 0.05f))
            .border(1.dp, if (sel) HFColors.StatusBlue.copy(alpha = 0.4f) else HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (sel) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Step(g: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(g, color = HFColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Lenient time parsing — a tech should not have to match a format.
 * "8", "8:00", "8:00 AM", "0800" and "1630" all resolve.
 *
 * A bare hour of 1-7 reads as PM: nobody logs a hangar shift starting at
 * three in the morning, and reading it as AM would silently add twelve
 * hours to their pay.
 */
internal fun parseTimeLoose(raw: String): LocalTime? {
    val t = raw.trim().uppercase()
    if (t.isEmpty()) return null
    val pm = t.contains("PM"); val am = t.contains("AM")
    val d = t.filter { it.isDigit() || it == ':' }
    if (d.isEmpty()) return null
    var hour: Int; var minute = 0
    if (d.contains(':')) {
        val p = d.split(':')
        hour = p[0].toIntOrNull() ?: return null
        minute = p.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toIntOrNull() ?: return null
    } else when (d.length) {
        1, 2 -> hour = d.toIntOrNull() ?: return null
        3 -> { hour = d.substring(0, 1).toInt(); minute = d.substring(1).toInt() }
        4 -> { hour = d.substring(0, 2).toInt(); minute = d.substring(2).toInt() }
        else -> return null
    }
    if (pm && hour in 1..11) hour += 12
    if (am && hour == 12) hour = 0
    if (!pm && !am && !d.contains(':') && d.length <= 2 && hour in 1..7) hour += 12
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

private fun span(e: HFTimeEntry): String {
    val f = DateTimeFormatter.ofPattern("h:mm a")
    val s = e.startedAt?.let { runCatching { java.time.Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalTime() }.getOrNull() }
    val en = e.endedAt?.let { runCatching { java.time.Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalTime() }.getOrNull() }
    return if (s != null && en != null) "${s.format(f)} – ${en.format(f)}" else "Logged time"
}

private fun hrs(m: Int): String {
    val h = m / 60; val r = m % 60
    return when { h == 0 && r == 0 -> "0h"; h == 0 -> "${r}m"; r == 0 -> "${h}h"; else -> "${h}h ${r}m" }
}
