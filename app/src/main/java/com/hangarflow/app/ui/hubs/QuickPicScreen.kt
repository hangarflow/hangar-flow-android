package com.hangarflow.app.ui.hubs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.EquipmentDueSeverity
import com.hangarflow.app.data.model.HFAuditEvent
import com.hangarflow.app.data.model.HFEquipment
import com.hangarflow.app.data.model.HFEquipmentMaintenanceItem
import com.hangarflow.app.data.model.HFPartLocation
import com.hangarflow.app.data.model.dueInfo
import com.hangarflow.app.ui.common.hfPressClickable
import com.hangarflow.app.ui.theme.HFColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * QuickPic — scan a printed Hangar Flow QR label with the phone camera.
 *
 * Tokens are printed on the Desktop side:
 *   HFP:{part_id}       → inventory part  → "how many are you using?"
 *   HFE:{equipment_id}  → shop equipment  → due / last-served info card
 *
 * The scanner (zxing-android-embedded) ships its own CaptureActivity that
 * asks for the camera permission itself, so there's zero setup.
 */

private sealed interface ScanTarget {
    data class Part(val part: HFPartLocation) : ScanTarget
    data class Equipment(val equipment: HFEquipment) : ScanTarget
    data class Unknown(val raw: String) : ScanTarget
}

@Composable
fun QuickPicScreen() {
    val state by SharedStore.state.collectAsState()
    var target by remember { mutableStateOf<ScanTarget?>(null) }
    var mode by remember { mutableStateOf("landing") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        target = resolveToken(contents)
    }

    fun launchScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Point at a Hangar Flow QR label")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            val dur = 260
            if (targetState == "labels") {
                (slideInHorizontally(tween(dur)) { it } + fadeIn(tween(dur))) togetherWith
                    (slideOutHorizontally(tween(dur)) { -it / 5 } + fadeOut(tween(dur)))
            } else {
                (slideInHorizontally(tween(dur)) { -it / 5 } + fadeIn(tween(dur))) togetherWith
                    (slideOutHorizontally(tween(dur)) { it } + fadeOut(tween(dur)))
            }
        },
        label = "quickpic-mode"
    ) { m ->
        if (m == "labels") {
            QuickPicLabelBuilder(onBack = { mode = "landing" })
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "Scan a QR label to pull a part or check equipment, or print labels to stick on your gear and shelves.",
                    color = HFColors.OnSurface.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                QpLandingTile("Scan a Label", "Camera → part usage or equipment due", Icons.Outlined.QrCodeScanner, HFColors.StatusCyan) { launchScan() }
                QpLandingTile("Print QR Labels", "Make a sheet for parts & equipment", Icons.Outlined.QrCode2, HFColors.StatusGreen) { mode = "labels" }
                Text(
                    "${state.partLocations.size} parts • ${state.equipment.size} pieces of gear on file",
                    color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    when (val t = target) {
        is ScanTarget.Part -> PartUsageSheet(t.part, onDismiss = { target = null })
        is ScanTarget.Equipment -> EquipmentInfoSheet(t.equipment, onDismiss = { target = null })
        is ScanTarget.Unknown -> UnknownCodeSheet(t.raw, onDismiss = { target = null })
        null -> {}
    }
}

@Composable
private fun QpLandingTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .hfPressClickable(onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HFColors.OnSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Parse the typed token against the live store. */
private fun resolveToken(raw: String): ScanTarget {
    val trimmed = raw.trim()
    val state = SharedStore.state.value
    return when {
        trimmed.startsWith("HFP:") -> {
            val id = trimmed.removePrefix("HFP:")
            state.partLocations.firstOrNull { it.id == id }?.let { ScanTarget.Part(it) }
                ?: ScanTarget.Unknown(trimmed)
        }
        trimmed.startsWith("HFE:") -> {
            val id = trimmed.removePrefix("HFE:")
            state.equipment.firstOrNull { it.id == id }?.let { ScanTarget.Equipment(it) }
                ?: ScanTarget.Unknown(trimmed)
        }
        else -> ScanTarget.Unknown(trimmed)
    }
}

// ---------- part usage sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartUsageSheet(part: HFPartLocation, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var count by remember { mutableStateOf("1") }
    var history by remember { mutableStateOf<List<HFAuditEvent>>(emptyList()) }
    LaunchedEffect(part.id) { history = SharedStore.fetchAuditEventsForEntity(part.id) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HFColors.Background, contentColor = HFColors.OnSurface, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(part.partName.ifBlank { "Part" }, color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (part.partNumber.isNotBlank()) QPTag("PN ${part.partNumber}", HFColors.StatusCyan)
                if (part.location.isNotBlank()) QPTag(part.location, HFColors.StatusBlue)
            }
            Text("In stock: ${part.quantity}", color = HFColors.OnSurface.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            LabelQP("How many are you using?")
            OutlinedTextField(
                value = count,
                onValueChange = { s -> count = s.filter { it.isDigit() }.take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = qpFieldColors()
            )
            // Quick steppers.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5, 10).forEach { n ->
                    QPChip("$n") { count = n.toString() }
                }
            }

            val n = count.toIntOrNull() ?: 0
            val ok = n in 1..part.quantity
            if (n > part.quantity) {
                Text("Only ${part.quantity} in stock.", color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            QPPrimary(if (ok) "Use $n — set stock to ${part.quantity - n}" else "Enter 1–${part.quantity}", enabled = ok) {
                SharedStore.consumePartLocation(part.id, n)
                // Record who used it so the part carries a "who last used it" trail.
                SharedStore.logAudit("inventory_part", part.id, "used", "Used $n — stock now ${part.quantity - n}")
                onDismiss()
            }
            if (history.isNotEmpty()) ScanHistorySection("Recent Activity", history)
            Spacer(Modifier.size(24.dp))
        }
    }
}

// ---------- equipment info sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentInfoSheet(equipment: HFEquipment, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by SharedStore.state.collectAsState()
    val eq = state.equipment.firstOrNull { it.id == equipment.id } ?: equipment
    val items = state.equipmentMaintenanceItems.filter { it.equipmentId == eq.id }
    val serviceLog = state.equipmentServiceLog.filter { it.equipmentId == eq.id }
    var logOpen by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<HFAuditEvent>>(emptyList()) }
    LaunchedEffect(eq.id) { history = SharedStore.fetchAuditEventsForEntity(eq.id) }

    val nextMaint = items.filter { it.itemKind == "maintenance" }.minByOrNull { rank(it.dueInfo(eq.usageHours).severity) }
    val nextCal = items.filter { it.itemKind == "calibration" }.minByOrNull { rank(it.dueInfo(eq.usageHours).severity) }
    val lastServed = serviceLog.maxByOrNull { it.performedAt }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HFColors.Background, contentColor = HFColors.OnSurface, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(eq.name.ifBlank { "Equipment" }, color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val sub = listOf(eq.equipmentType, eq.location).filter { it.isNotBlank() }.joinToString(" • ")
            if (sub.isNotBlank()) Text(sub, color = HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)

            InfoRow("Hour meter", "${fmtHrsQP(eq.usageHours)} hrs", HFColors.StatusBlue)

            val oil = nextMaint?.dueInfo(eq.usageHours)
            InfoRow(
                "Next service",
                if (nextMaint != null) "${nextMaint.title} — ${oil?.label}" else "None scheduled",
                oil?.severity?.let { sevColor(it) } ?: HFColors.OnSurfaceFaint
            )
            val cal = nextCal?.dueInfo(eq.usageHours)
            InfoRow(
                "Next calibration",
                if (nextCal != null) "${nextCal.title} — ${cal?.label}" else "None scheduled",
                cal?.severity?.let { sevColor(it) } ?: HFColors.OnSurfaceFaint
            )
            InfoRow(
                "Last served",
                lastServed?.let {
                    var v = it.performedAt.take(10)
                    it.hoursAtService?.let { h -> v += " @ ${fmtHrsQP(h)} hrs" }
                    if (it.performedByUserName.isNotBlank()) v += " by ${it.performedByUserName}"
                    v
                } ?: "Never",
                HFColors.OnSurface.copy(alpha = 0.75f)
            )

            // Usage-based "service due" summary.
            val usageItems = items.filter { it.intervalType == "usage" && it.nextDueHours != null }
            if (usageItems.isNotEmpty()) {
                LabelQP("Service due (hours)")
                usageItems.forEach { item ->
                    val remaining = (item.nextDueHours ?: 0.0) - eq.usageHours
                    val c = sevColor(item.dueInfo(eq.usageHours).severity)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, color = HFColors.OnSurface.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            if (remaining < 0) "OVERDUE ${fmtHrsQP(-remaining)} hrs" else "${fmtHrsQP(remaining)} hrs left",
                            color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            QPPrimary("Log a service", enabled = true) { logOpen = true }
            if (history.isNotEmpty()) ScanHistorySection("Recent Activity", history)
            Spacer(Modifier.size(24.dp))
        }
    }

    if (logOpen) {
        // Reuse the equipment hub's log-service flow via a lightweight local sheet.
        QuickLogServiceSheet(equipmentId = eq.id, currentHours = eq.usageHours, onDismiss = { logOpen = false; onDismiss() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogServiceSheet(equipmentId: String, currentHours: Double, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var performedAt by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var hours by remember { mutableStateOf(if (currentHours > 0) fmtHrsQP(currentHours) else "") }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HFColors.Background, contentColor = HFColors.OnSurface, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log a Service", color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LabelQP("Performed (YYYY-MM-DD)")
            OutlinedTextField(performedAt, { performedAt = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = qpFieldColors())
            LabelQP("Hours at service")
            OutlinedTextField(hours, { s -> hours = s.filter { it.isDigit() || it == '.' }.take(8) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = qpFieldColors())
            LabelQP("Notes")
            OutlinedTextField(notes, { notes = it }, singleLine = false, modifier = Modifier.fillMaxWidth(), colors = qpFieldColors())
            if (error != null) Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            QPPrimary(if (busy) "Saving…" else "Save Service", enabled = !busy) {
                busy = true; error = null
                scope.launch {
                    when (val r = SharedStore.logEquipmentService(
                        equipmentId = equipmentId,
                        maintenanceItemId = null,
                        performedAt = performedAt.trim(),
                        hoursAtService = hours.toDoubleOrNull(),
                        notes = notes
                    )) {
                        SharedStore.CreateResult.Success -> onDismiss()
                        is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

// ---------- unknown ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownCodeSheet(raw: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HFColors.Background, contentColor = HFColors.OnSurface, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Not a Hangar Flow label", color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "This QR code doesn't match a part or piece of equipment in this shop. Print labels from the Windows app's QuickPic tool, or the item may have been deleted.",
                color = HFColors.OnSurface.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Text(raw, color = HFColors.OnSurface.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            QPPrimary("Close", enabled = true, onClick = onDismiss)
            Spacer(Modifier.size(20.dp))
        }
    }
}

// ---------- shared bits ----------

private fun rank(s: EquipmentDueSeverity): Int = when (s) {
    EquipmentDueSeverity.OVERDUE -> 0
    EquipmentDueSeverity.DUE_SOON -> 1
    EquipmentDueSeverity.OK -> 2
    EquipmentDueSeverity.UNKNOWN -> 3
}

private fun sevColor(s: EquipmentDueSeverity): Color = when (s) {
    EquipmentDueSeverity.OK -> HFColors.StatusGreen
    EquipmentDueSeverity.DUE_SOON -> HFColors.StatusYellow
    EquipmentDueSeverity.OVERDUE -> HFColors.StatusRed
    EquipmentDueSeverity.UNKNOWN -> HFColors.OnSurfaceFaint
}

/** "Who last used / serviced / touched it" — from the org audit log. */
@Composable
private fun ScanHistorySection(title: String, events: List<HFAuditEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LabelQP(title)
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.04f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            events.take(8).forEach { e ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.padding(top = 5.dp).size(7.dp)
                            .clip(RoundedCornerShape(4.dp)).background(actionColor(e.action))
                    )
                    Spacer(Modifier.size(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            e.summary.ifBlank { e.action.replaceFirstChar { it.uppercase() } },
                            color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${e.actorName.ifBlank { "Someone" }} • ${relTime(e.createdAt)}",
                            color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun actionColor(action: String): Color = when (action) {
    "used" -> HFColors.StatusOrange
    "serviced" -> HFColors.StatusGreen
    "created" -> HFColors.StatusBlue
    "updated" -> HFColors.StatusCyan
    "deleted" -> HFColors.StatusRed
    else -> HFColors.OnSurface.copy(alpha = 0.6f)
}

private fun relTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { java.time.Instant.parse(iso) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: return ""
    val secs = java.time.Duration.between(then, java.time.Instant.now()).seconds
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86400 -> "${secs / 3600}h ago"
        secs < 2592000 -> "${secs / 86400}d ago"
        else -> "${secs / 2592000}mo ago"
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label.uppercase(), color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LabelQP(text: String) {
    Text(text.uppercase(), color = HFColors.OnSurface.copy(alpha = 0.60f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp)
}

@Composable
private fun QPTag(text: String, color: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QPChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(HFColors.OnSurface.copy(alpha = 0.08f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.20f), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QPPrimary(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (!enabled) HFColors.OnSurface.copy(alpha = 0.10f) else HFColors.OnSurface)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HFColors.BrandInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun fmtHrsQP(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

@Composable
private fun qpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
    focusedTextColor = HFColors.OnSurface,
    unfocusedTextColor = HFColors.OnSurface,
    cursorColor = HFColors.OnSurface
)
