package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFEmployeePay
import com.hangarflow.app.data.model.HFTimeEntry
import com.hangarflow.app.data.model.HFUserProfile
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Admin hours approval. NO MONEY — this used to show each person's dollars
 * owed and their hourly rate, with a "Set pay / Edit pay" button opening a rate
 * editor, on a phone. Wages belong on the desktops.
 *
 * What stays is the shop-floor half: approved vs pending hours, and approving
 * the pending ones. Hours are not money.
 *
 * Pay is computed only from `payRateApplied` — the rate stamped on an
 * entry when it was approved — never from the employee's current rate.
 * Give someone a raise and last period's total must not move.
 */
@Composable
fun PayrollHub() {
    val state by SharedStore.state.collectAsState()
    val auth by com.hangarflow.app.auth.AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    if (!auth.isAdmin) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Payroll is admin-only.", color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Payroll", color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Rates and pay are on the desktop. Approving here only moves hours.",
            color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)

        state.users.filter { it.isActive }.forEach { user ->
            val pay = state.employeePay.firstOrNull { it.userId == user.authUserId || it.userId == user.id }
            val (start, end) = periodFor(pay, today)
            val mine = state.timeEntries.filter { belongs(it, user) && inRange(it, start, end) }
            val approved = mine.filter { it.approvalStatus == "approved" }
            val pending = mine.filter { it.approvalStatus == "pending" }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.04f))
                    .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName.ifBlank { "Unnamed" },
                            color = HFColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${scheduleLabel(pay)} · ${label(start, end)}",
                            color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat("APPROVED", hrs(approved.sumOf { it.minutesWorked }), HFColors.StatusGreen)
                    Stat("PENDING", hrs(pending.sumOf { it.minutesWorked }),
                        if (pending.isNotEmpty()) HFColors.StatusOrange else HFColors.OnSurface.copy(alpha = 0.4f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pending.isNotEmpty()) {
                        Btn(if (busy == user.id) "Approving…" else "Approve ${hrs(pending.sumOf { it.minutesWorked })}",
                            HFColors.StatusGreen) {
                            busy = user.id
                            scope.launch {
                                SharedStore.decideTimeEntries(pending.map { it.id }, approve = true)
                                busy = null
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

}

@OptIn(ExperimentalMaterial3Api::class)
// SetPaySheet USED TO LIVE HERE — an hourly-rate editor on a phone. Wages
// belong on the desktops, so it and its "Set pay / Edit pay" button are gone
// rather than merely hidden. Rates are set from the Windows payroll panel
// (ui/time/PayrollPanel.kt).

@Composable private fun Stat(l: String, v: String, c: androidx.compose.ui.graphics.Color) {
    Column { Text(l, color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(v, color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable private fun Btn(l: String, c: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(100.dp)).background(c.copy(alpha = 0.14f))
        .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(l, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

// ---- period + helpers -------------------------------------------------

/** floorDiv, not plain division: for dates before the anchor the day
 *  difference is negative and truncation toward zero would place them in
 *  the wrong fortnight, overlapping historical periods. */
private fun periodFor(pay: HFEmployeePay?, on: LocalDate): Pair<LocalDate, LocalDate> = when (pay?.paySchedule) {
    "weekly" -> {
        val s = on.minusDays(((on.dayOfWeek.value + 6) % 7).toLong()); s to s.plusDays(6)
    }
    "monthly" -> on.withDayOfMonth(1) to on.withDayOfMonth(on.lengthOfMonth())
    else -> {
        val anchor = pay?.payAnchorDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(2026, 1, 5)
        val idx = Math.floorDiv(ChronoUnit.DAYS.between(anchor, on), 14L)
        val s = anchor.plusDays(idx * 14L); s to s.plusDays(13)
    }
}

private fun scheduleLabel(p: HFEmployeePay?): String = when (p?.paySchedule) {
    "weekly" -> "Weekly"; "monthly" -> "Monthly"; else -> "Bi-weekly"
}

private fun label(s: LocalDate, e: LocalDate): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("MMM d")
    return "${s.format(f)} – ${e.format(f)}"
}

private fun belongs(e: HFTimeEntry, u: HFUserProfile): Boolean = when {
    e.userId != null && u.authUserId != null && e.userId == u.authUserId -> true
    e.userId != null && e.userId == u.id -> true
    e.userId == null && e.userName.isNotBlank() -> e.userName.equals(u.displayName, ignoreCase = true)
    else -> false
}

private fun inRange(e: HFTimeEntry, s: LocalDate, en: LocalDate): Boolean {
    val d = runCatching { LocalDate.parse((e.startedAt ?: e.entryDate).take(10)) }.getOrNull() ?: return false
    return !d.isBefore(s) && !d.isAfter(en)
}

private fun hrs(m: Int): String {
    val h = m / 60; val r = m % 60
    return when { h == 0 && r == 0 -> "0h"; h == 0 -> "${r}m"; r == 0 -> "${h}h"; else -> "${h}h ${r}m" }
}


