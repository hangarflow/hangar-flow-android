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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.EquipmentDueSeverity
import com.hangarflow.app.data.model.HFEquipment
import com.hangarflow.app.data.model.HFEquipmentMaintenanceItem
import com.hangarflow.app.data.model.HFEquipmentServiceEntry
import com.hangarflow.app.data.model.dueInfo
import com.hangarflow.app.data.model.worstDueSeverity
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.common.hfPressClickable
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

// Shop-gear equipment types offered as quick-pick chips.
private val EQUIPMENT_TYPES = listOf(
    "Tug", "Compressor", "GPU", "Jack", "Lift", "Forklift",
    "Torque Wrench", "Gauge", "Test Set", "Shop Truck", "General"
)

private fun EquipmentDueSeverity.color(): Color = when (this) {
    EquipmentDueSeverity.OK -> HFColors.StatusGreen
    EquipmentDueSeverity.DUE_SOON -> HFColors.StatusYellow
    EquipmentDueSeverity.OVERDUE -> HFColors.StatusRed
    EquipmentDueSeverity.UNKNOWN -> HFColors.OnSurfaceFaint
}

// ---------- hub entry ----------

@Composable
fun EquipmentHub() {
    HFPullToRefreshHost { EquipmentHubContent() }
}

@Composable
private fun EquipmentHubContent() {
    val state by SharedStore.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<HFEquipment?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<String?>(null) }

    val filtered = remember(state.equipment, query) {
        val q = query.trim().lowercase()
        state.equipment.filter { e ->
            q.isEmpty() ||
                e.name.lowercase().contains(q) ||
                e.equipmentType.lowercase().contains(q) ||
                e.location.lowercase().contains(q) ||
                e.manufacturer.lowercase().contains(q) ||
                e.modelNumber.lowercase().contains(q) ||
                e.serialNumber.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = HFColors.OnSurface.copy(alpha = 0.55f)) },
                placeholder = {
                    Text("Search gear, type, location…", color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = equipFieldColors()
            )
        }

        item { EquipmentSummary(state.equipment, state.equipmentMaintenanceItems) }

        item { AddEquipmentButton(onClick = { createOpen = true }) }

        item {
            Text(
                if (query.isBlank()) "${filtered.size} pieces of gear" else "${filtered.size} of ${state.equipment.size}",
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.equipment.isEmpty()) {
            item {
                IOSPlaceholderPanel(
                    message = "No equipment tracked yet. Tap Add Equipment to log a tug, compressor, jack, or a calibrated tool — everyone in the shop sees the same due list."
                )
            }
        } else if (filtered.isEmpty()) {
            item { IOSPlaceholderPanel(message = "No gear matches your search.") }
        } else {
            items(filtered, key = { it.id }) { eq ->
                val items = state.equipmentMaintenanceItems.filter { it.equipmentId == eq.id }
                EquipmentCard(eq, items, onClick = { detailFor = eq.id })
            }
        }
    }

    if (createOpen) {
        EquipmentEditSheet(existing = null, onDismiss = { createOpen = false })
    }
    editing?.let { eq ->
        EquipmentEditSheet(existing = eq, onDismiss = { editing = null })
    }
    detailFor?.let { id ->
        val eq = state.equipment.firstOrNull { it.id == id }
        if (eq == null) {
            detailFor = null
        } else {
            EquipmentDetailSheet(
                equipment = eq,
                items = state.equipmentMaintenanceItems.filter { it.equipmentId == id },
                serviceLog = state.equipmentServiceLog.filter { it.equipmentId == id },
                onEdit = { editing = eq; detailFor = null },
                onDismiss = { detailFor = null }
            )
        }
    }
}

// ---------- summary strip ----------

@Composable
private fun EquipmentSummary(equipment: List<HFEquipment>, items: List<HFEquipmentMaintenanceItem>) {
    val overdue = equipment.count { eq ->
        worstDueSeverity(items.filter { it.equipmentId == eq.id }, eq.usageHours) == EquipmentDueSeverity.OVERDUE
    }
    val soon = equipment.count { eq ->
        worstDueSeverity(items.filter { it.equipmentId == eq.id }, eq.usageHours) == EquipmentDueSeverity.DUE_SOON
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EquipStat("GEAR", equipment.size.toString(), HFColors.StatusCyan, Modifier.weight(1f))
        EquipStat("DUE SOON", soon.toString(), HFColors.StatusYellow, Modifier.weight(1f))
        EquipStat("OVERDUE", overdue.toString(), HFColors.StatusRed, Modifier.weight(1f))
    }
}

@Composable
private fun EquipStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.size(6.dp))
        Text(value, color = HFColors.OnSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddEquipmentButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Add, null, tint = HFColors.OnSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Add Equipment", color = HFColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- list card ----------

@Composable
private fun EquipmentCard(
    eq: HFEquipment,
    items: List<HFEquipmentMaintenanceItem>,
    onClick: () -> Unit
) {
    val severity = worstDueSeverity(items, eq.usageHours)
    val accent = severity.color()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hfPressClickable(onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                eq.name.ifBlank { "Untitled equipment" },
                color = HFColors.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            DueBadge(severity)
        }
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (eq.equipmentType.isNotBlank()) {
                InlineTagE(eq.equipmentType, HFColors.StatusCyan); Spacer(Modifier.size(6.dp))
            }
            if (eq.usageHours > 0) {
                InlineTagE("${fmtHours(eq.usageHours)} hrs", HFColors.StatusBlue); Spacer(Modifier.size(6.dp))
            }
            if (eq.location.isNotBlank()) {
                Text(eq.location, color = HFColors.OnSurface.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Nearest due item preview.
        val nearest = items.minByOrNull { severityRank(it.dueInfo(eq.usageHours).severity) }
        if (nearest != null) {
            Spacer(Modifier.size(8.dp))
            val info = nearest.dueInfo(eq.usageHours)
            Text(
                "${if (nearest.itemKind == "calibration") "Cal" else "Svc"}: ${nearest.title} — ${info.label}",
                color = info.severity.color(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun severityRank(s: EquipmentDueSeverity): Int = when (s) {
    EquipmentDueSeverity.OVERDUE -> 0
    EquipmentDueSeverity.DUE_SOON -> 1
    EquipmentDueSeverity.OK -> 2
    EquipmentDueSeverity.UNKNOWN -> 3
}

@Composable
private fun DueBadge(severity: EquipmentDueSeverity) {
    val (label, color) = when (severity) {
        EquipmentDueSeverity.OK -> "OK" to HFColors.StatusGreen
        EquipmentDueSeverity.DUE_SOON -> "DUE SOON" to HFColors.StatusYellow
        EquipmentDueSeverity.OVERDUE -> "OVERDUE" to HFColors.StatusRed
        EquipmentDueSeverity.UNKNOWN -> "NO DUES" to HFColors.OnSurfaceFaint
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InlineTagE(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- create/edit sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentEditSheet(existing: HFEquipment?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.equipmentType?.ifBlank { "General" } ?: "General") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var manufacturer by remember { mutableStateOf(existing?.manufacturer ?: "") }
    var modelNumber by remember { mutableStateOf(existing?.modelNumber ?: "") }
    var serialNumber by remember { mutableStateOf(existing?.serialNumber ?: "") }
    var usageHours by remember { mutableStateOf(existing?.usageHours?.let { if (it > 0) fmtHours(it) else "" } ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Background,
        contentColor = HFColors.OnSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (existing == null) "Add Equipment" else "Edit Equipment",
                    color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (existing != null) {
                    DeleteChip {
                        SharedStore.deleteEquipment(existing.id)
                        onDismiss()
                    }
                }
            }

            EquipField("Name", name, { name = it }, "Tug #1")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelE("Type")
                ChipScrollRow {
                    EQUIPMENT_TYPES.forEach { t ->
                        ChipPill(label = t, active = type == t) { type = t }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EquipField("Location", location, { location = it }, "Hangar bay", modifier = Modifier.weight(1.4f))
                EquipField("Usage Hours", usageHours, { s -> usageHours = s.filter { it.isDigit() || it == '.' }.take(8) }, "0", KeyboardType.Number, Modifier.weight(1f))
            }
            EquipField("Manufacturer", manufacturer, { manufacturer = it }, "Tronair")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EquipField("Model", modelNumber, { modelNumber = it }, "M-100", modifier = Modifier.weight(1f))
                EquipField("Serial", serialNumber, { serialNumber = it }, "SN-0001", modifier = Modifier.weight(1f))
            }
            EquipField("Notes", notes, { notes = it }, "Condition or context", singleLine = false)

            if (error != null) Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            PrimaryButton(if (busy) "Saving…" else if (existing == null) "Save Equipment" else "Update Equipment", enabled = !busy) {
                busy = true; error = null
                scope.launch {
                    val draft = (existing ?: HFEquipment(id = "", orgId = "")).copy(
                        name = name,
                        equipmentType = type,
                        location = location,
                        manufacturer = manufacturer,
                        modelNumber = modelNumber,
                        serialNumber = serialNumber,
                        usageHours = usageHours.toDoubleOrNull() ?: (existing?.usageHours ?: 0.0),
                        notes = notes
                    )
                    when (val r = SharedStore.saveEquipment(draft)) {
                        SharedStore.CreateResult.Success -> onDismiss()
                        is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                    }
                }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}

// ---------- detail sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentDetailSheet(
    equipment: HFEquipment,
    items: List<HFEquipmentMaintenanceItem>,
    serviceLog: List<HFEquipmentServiceEntry>,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var addItemOpen by remember { mutableStateOf(false) }
    var logServiceFor by remember { mutableStateOf<Pair<String?, Boolean>?>(null) } // (maintenanceItemId, open)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Background,
        contentColor = HFColors.OnSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(equipment.name.ifBlank { "Equipment" }, color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val sub = listOf(equipment.equipmentType, equipment.location).filter { it.isNotBlank() }.joinToString(" • ")
                    if (sub.isNotBlank()) Text(sub, color = HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                SecondaryChip("Edit", onEdit)
            }

            // Usage hour meter quick-edit.
            UsageHoursRow(equipment)

            // ---- maintenance / calibration items ----
            SectionHeader("Maintenance & Calibration")
            if (items.isEmpty()) {
                Text("No due items yet. Add an oil change, inspection, or a calibration schedule.", color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            } else {
                items.sortedBy { severityRank(it.dueInfo(equipment.usageHours).severity) }.forEach { item ->
                    MaintenanceItemRow(
                        item = item,
                        usageHours = equipment.usageHours,
                        onLogService = { logServiceFor = item.id to true },
                        onDelete = { SharedStore.deleteMaintenanceItem(item.id) }
                    )
                }
            }
            SecondaryChip("+ Add maintenance / calibration item") { addItemOpen = true }

            // ---- service history ----
            SectionHeader("Service History")
            if (serviceLog.isEmpty()) {
                Text("Nothing logged yet.", color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            } else {
                serviceLog.sortedByDescending { it.performedAt }.forEach { entry ->
                    ServiceLogRow(entry, onDelete = { SharedStore.deleteServiceEntry(entry.id) })
                }
            }
            SecondaryChip("+ Log a service") { logServiceFor = null to true }

            Spacer(Modifier.size(28.dp))
        }
    }

    if (addItemOpen) {
        MaintenanceItemSheet(equipmentId = equipment.id, onDismiss = { addItemOpen = false })
    }
    logServiceFor?.let { (itemId, _) ->
        LogServiceSheet(
            equipmentId = equipment.id,
            maintenanceItemId = itemId,
            currentHours = equipment.usageHours,
            onDismiss = { logServiceFor = null }
        )
    }
}

@Composable
private fun UsageHoursRow(equipment: HFEquipment) {
    var editing by remember(equipment.id) { mutableStateOf(false) }
    var value by remember(equipment.id) { mutableStateOf(fmtHours(equipment.usageHours)) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.StatusBlue.copy(alpha = 0.30f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hour Meter", color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${fmtHours(equipment.usageHours)} hrs", color = HFColors.StatusBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { s -> value = s.filter { it.isDigit() || it == '.' }.take(8) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = equipFieldColors()
                )
                SecondaryChip("Save") {
                    SharedStore.logEquipmentHours(equipment.id, value.toDoubleOrNull() ?: equipment.usageHours)
                    editing = false
                }
            }
        } else {
            SecondaryChip("Update hours") { editing = true }
        }
    }
}

@Composable
private fun MaintenanceItemRow(
    item: HFEquipmentMaintenanceItem,
    usageHours: Double,
    onLogService: () -> Unit,
    onDelete: () -> Unit
) {
    val info = item.dueInfo(usageHours)
    val accent = info.severity.color()
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InlineTagE(if (item.itemKind == "calibration") "CAL" else "MAINT", if (item.itemKind == "calibration") HFColors.StatusPurple else HFColors.StatusCyan)
            Spacer(Modifier.size(8.dp))
            Text(item.title, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(info.label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val interval = when {
            item.intervalType == "usage" && item.intervalHours != null -> "Every ${fmtHours(item.intervalHours!!)} hrs"
            item.intervalType == "time" && item.intervalMonths != null -> "Every ${item.intervalMonths} mo"
            else -> null
        }
        if (interval != null) {
            Text(interval, color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryChip("Log service", onLogService)
            DeleteChip(onDelete)
        }
    }
}

@Composable
private fun ServiceLogRow(entry: HFEquipmentServiceEntry, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HFColors.OnSurface.copy(alpha = 0.03f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.performedAt.take(10), color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (entry.hoursAtService != null) {
                Text("${fmtHours(entry.hoursAtService!!)} hrs", color = HFColors.StatusBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (entry.notes.isNotBlank()) {
            Text(entry.notes, color = HFColors.OnSurface.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.performedByUserName.isNotBlank()) {
                Text("by ${entry.performedByUserName}", color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            DeleteChip(onDelete)
        }
    }
}

// ---------- add maintenance item sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceItemSheet(equipmentId: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by SharedStore.state.collectAsState()
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("maintenance") }
    var intervalType by remember { mutableStateOf("time") }
    var intervalMonths by remember { mutableStateOf("") }
    var intervalHours by remember { mutableStateOf("") }
    var lastDoneAt by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var lastDoneHours by remember { mutableStateOf("") }
    var remindUserId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HFColors.Background, contentColor = HFColors.OnSurface, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Maintenance / Calibration", color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            EquipField("Title", title, { title = it }, "Oil change")

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelE("Kind")
                ChipScrollRow {
                    ChipPill("Maintenance", active = kind == "maintenance") { kind = "maintenance" }
                    ChipPill("Calibration", active = kind == "calibration", accent = HFColors.StatusPurple) { kind = "calibration" }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelE("Interval")
                ChipScrollRow {
                    ChipPill("Time-based", active = intervalType == "time") { intervalType = "time" }
                    ChipPill("Usage-based", active = intervalType == "usage") { intervalType = "usage" }
                }
            }
            if (intervalType == "time") {
                EquipField("Every N months", intervalMonths, { s -> intervalMonths = s.filter { it.isDigit() }.take(4) }, "12", KeyboardType.Number)
                EquipField("Last done (YYYY-MM-DD)", lastDoneAt, { lastDoneAt = it }, "2026-01-01")
            } else {
                EquipField("Every N hours", intervalHours, { s -> intervalHours = s.filter { it.isDigit() || it == '.' }.take(8) }, "100", KeyboardType.Number)
                EquipField("Last done at hours", lastDoneHours, { s -> lastDoneHours = s.filter { it.isDigit() || it == '.' }.take(8) }, "0", KeyboardType.Number)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelE("Remind (optional)")
                ChipScrollRow {
                    ChipPill("No reminder", active = remindUserId == null) { remindUserId = null }
                    state.users.forEach { u ->
                        ChipPill(u.displayName.ifBlank { "Tech" }, active = remindUserId == u.id) { remindUserId = u.id }
                    }
                }
                Text("Time-based items with a reminder drop a due date on the calendar and notify the picked tech.", color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            if (error != null) Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            PrimaryButton(if (busy) "Saving…" else "Save Item", enabled = !busy) {
                busy = true; error = null
                scope.launch {
                    val draft = HFEquipmentMaintenanceItem(
                        id = "",
                        orgId = "",
                        equipmentId = equipmentId,
                        title = title,
                        itemKind = kind,
                        intervalType = intervalType,
                        intervalMonths = if (intervalType == "time") intervalMonths.toIntOrNull() else null,
                        intervalHours = if (intervalType == "usage") intervalHours.toDoubleOrNull() else null,
                        lastDoneAt = if (intervalType == "time") lastDoneAt.trim().ifBlank { null } else null,
                        lastDoneHours = if (intervalType == "usage") lastDoneHours.toDoubleOrNull() else null,
                        remindUserId = remindUserId
                    )
                    when (val r = SharedStore.saveMaintenanceItem(draft)) {
                        SharedStore.CreateResult.Success -> onDismiss()
                        is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                    }
                }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}

// ---------- log service sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogServiceSheet(
    equipmentId: String,
    maintenanceItemId: String?,
    currentHours: Double,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var performedAt by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var hours by remember { mutableStateOf(if (currentHours > 0) fmtHours(currentHours) else "") }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HFColors.Background, contentColor = HFColors.OnSurface, dragHandle = null) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log a Service", color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (maintenanceItemId != null) {
                Text("This will reset the linked item's due clock.", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            EquipField("Performed (YYYY-MM-DD)", performedAt, { performedAt = it }, "2026-07-14")
            EquipField("Hours at service", hours, { s -> hours = s.filter { it.isDigit() || it == '.' }.take(8) }, "0", KeyboardType.Number)
            EquipField("Notes", notes, { notes = it }, "What was done", singleLine = false)

            if (error != null) Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            PrimaryButton(if (busy) "Saving…" else "Save Service", enabled = !busy) {
                busy = true; error = null
                scope.launch {
                    when (val r = SharedStore.logEquipmentService(
                        equipmentId = equipmentId,
                        maintenanceItemId = maintenanceItemId,
                        performedAt = performedAt.trim(),
                        hoursAtService = hours.toDoubleOrNull(),
                        notes = notes
                    )) {
                        SharedStore.CreateResult.Success -> onDismiss()
                        is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
                    }
                }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}

// ---------- shared bits ----------

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp)
}

@Composable
private fun LabelE(text: String) {
    Text(text.uppercase(), color = HFColors.OnSurface.copy(alpha = 0.60f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp)
}

@Composable
private fun EquipField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        LabelE(label)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            placeholder = { Text(placeholder, color = HFColors.OnSurface.copy(alpha = 0.35f), fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            colors = equipFieldColors()
        )
    }
}

@Composable
private fun ChipScrollRow(content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ChipPill(label: String, active: Boolean, accent: Color = HFColors.OnSurface, onClick: () -> Unit) {
    val bg = if (active) accent.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.06f)
    val fg = if (active) accent else HFColors.OnSurface
    val border = if (active) accent.copy(alpha = 0.55f) else HFColors.OnSurface.copy(alpha = 0.15f)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(bg).border(1.dp, border, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HFColors.OnSurface.copy(alpha = 0.08f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeleteChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HFColors.StatusRed.copy(alpha = 0.15f))
            .border(1.dp, HFColors.StatusRed.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Delete, null, tint = HFColors.StatusRed, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text("Delete", color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (!enabled) HFColors.OnSurface.copy(alpha = 0.10f) else HFColors.OnSurface)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HFColors.BrandInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun fmtHours(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

@Composable
private fun equipFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
    focusedTextColor = HFColors.OnSurface,
    unfocusedTextColor = HFColors.OnSurface,
    cursorColor = HFColors.OnSurface
)
