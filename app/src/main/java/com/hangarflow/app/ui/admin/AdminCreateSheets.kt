package com.hangarflow.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFWorkCategory
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * Admin-only create sheet. Three modes live behind one entry point:
 * plane / work log / invite user. Kept in one file because each form is
 * small and they share styling.
 */
enum class AdminCreateMode { Plane, WorkLog, Invite }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCreateSheet(
    mode: AdminCreateMode,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HFColors.Background,
        contentColor = HFColors.OnSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                when (mode) {
                    AdminCreateMode.Plane -> "New Plane"
                    AdminCreateMode.WorkLog -> "New Work Log"
                    AdminCreateMode.Invite -> "Invite Teammate"
                },
                color = HFColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(16.dp))
            when (mode) {
                AdminCreateMode.Plane -> CreatePlaneForm(onDone = onDismiss)
                AdminCreateMode.WorkLog -> CreateWorkLogForm(onDone = onDismiss)
                AdminCreateMode.Invite -> InviteUserForm(onDone = onDismiss)
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}

@Composable
private fun CreatePlaneForm(onDone: () -> Unit) {
    val state by SharedStore.state.collectAsState()
    var tail by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PALETTE.first()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedManualIds by remember { mutableStateOf(setOf<String>()) }
    // Receiving inspection: what the plane is coming in for. "" = none.
    var incomingInspection by remember { mutableStateOf("") }
    // Phase 4 — aircraft type + which type-matched manuals to attach.
    var aircraftType by remember { mutableStateOf("") }
    var selectedTypeManualIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // Existing manuals available to attach without re-uploading. Tail
    // matches float to the top so the obvious picks are first.
    val attachableManuals = remember(state.manuals, tail) {
        state.manuals
            .filter { it.sourceType == "manualPDF" || it.sourceType == "manualText" }
            .distinctBy { "${it.planeTailNumber?.uppercase()}:${it.fileName.lowercase()}" }
            .sortedByDescending {
                tail.isNotBlank() && it.planeTailNumber?.equals(tail.trim(), ignoreCase = true) == true
            }
    }

    // Returning plane: manuals already on file for this exact tail. These
    // get auto-attached on create (mirrors macOS/Desktop recall).
    val recalledManuals = remember(state.manuals, tail) {
        val t = tail.trim().uppercase()
        if (t.isBlank()) emptyList()
        else state.manuals.filter { (it.planeTailNumber ?: "").uppercase() == t }
    }

    FormField(label = "Tail Number", value = tail, onChange = { tail = it.uppercase() }, placeholder = "N123AB")
    Spacer(Modifier.size(10.dp))
    FormField(label = "Display Name", value = display, onChange = { display = it }, placeholder = "Pilatus PC12")
    Spacer(Modifier.size(12.dp))
    Label("Outline Color")
    Spacer(Modifier.size(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { hex ->
            val selected = hex == color
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(parseHex(hex))
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) HFColors.OnSurface else HFColors.OnSurface.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { color = hex }
            )
        }
    }

    // Receiving inspection — what the plane is coming in for. Chip row
    // matches the color picker idiom above.
    Spacer(Modifier.size(14.dp))
    Label("Coming in for (inspection)")
    Spacer(Modifier.size(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (listOf("") + INCOMING_INSPECTION_OPTIONS).forEach { opt ->
            val selected = incomingInspection == opt
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        if (selected) HFColors.StatusBlue.copy(alpha = 0.20f)
                        else HFColors.OnSurface.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) HFColors.StatusBlue.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.12f),
                        RoundedCornerShape(100.dp)
                    )
                    .clickable { incomingInspection = opt }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    if (opt.isBlank()) "None" else opt,
                    color = if (selected) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.75f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (recalledManuals.isNotEmpty()) {
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HFColors.StatusBlue.copy(alpha = 0.12f))
                .border(1.dp, HFColors.StatusBlue.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                "We've worked on ${tail.trim().uppercase()} before — ${recalledManuals.size} manual${if (recalledManuals.size == 1) "" else "s"} on file. They'll be re-attached automatically.",
                color = HFColors.StatusBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }

    // Phase 4 — aircraft type/model. Manuals tagged with that type are
    // suggested for attach (checked by default).
    Spacer(Modifier.size(14.dp))
    FormField(label = "Aircraft Type / Model", value = aircraftType, onChange = { aircraftType = it }, placeholder = "Pilatus PC-12")
    val knownTypes = remember(state.manuals, state.planes) { SharedStore.knownAircraftTypes() }
    if (knownTypes.isNotEmpty()) {
        Spacer(Modifier.size(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            knownTypes.forEach { t ->
                val sel = aircraftType.trim().equals(t, ignoreCase = true)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(100.dp))
                        .background(if (sel) HFColors.StatusBlue.copy(alpha = 0.20f) else HFColors.OnSurface.copy(alpha = 0.06f))
                        .border(1.dp, if (sel) HFColors.StatusBlue.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                        .clickable { aircraftType = t }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(t, color = if (sel) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    val typeSuggestions = remember(aircraftType, state.manuals, tail) {
        val recallIds = recalledManuals.map { it.id }.toSet()
        SharedStore.manualsForType(aircraftType).filter { it.id !in recallIds }
    }
    LaunchedEffect(aircraftType) {
        selectedTypeManualIds = SharedStore.manualsForType(aircraftType).map { it.id }.toSet()
    }
    if (typeSuggestions.isNotEmpty()) {
        Spacer(Modifier.size(8.dp))
        Label("${typeSuggestions.size} manual${if (typeSuggestions.size == 1) "" else "s"} on file for this type")
        Spacer(Modifier.size(6.dp))
        typeSuggestions.forEach { m ->
            val checked = m.id in selectedTypeManualIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (checked) HFColors.StatusBlue.copy(alpha = 0.14f) else HFColors.OnSurface.copy(alpha = 0.05f))
                    .clickable {
                        selectedTypeManualIds = if (checked) selectedTypeManualIds - m.id else selectedTypeManualIds + m.id
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (checked) "☑" else "☐", color = if (checked) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 14.sp)
                Spacer(Modifier.size(8.dp))
                Text(m.title.takeIf { it.isNotBlank() } ?: m.fileName, color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(6.dp))
        }
    }

    if (attachableManuals.isNotEmpty()) {
        Spacer(Modifier.size(14.dp))
        Label("Attach Existing Files (optional)")
        Spacer(Modifier.size(6.dp))
        attachableManuals.take(20).forEach { m ->
            val checked = m.id in selectedManualIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (checked) HFColors.StatusBlue.copy(alpha = 0.14f)
                        else HFColors.OnSurface.copy(alpha = 0.05f)
                    )
                    .clickable {
                        selectedManualIds = if (checked) selectedManualIds - m.id else selectedManualIds + m.id
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (checked) "☑" else "☐",
                    color = if (checked) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title.takeIf { it.isNotBlank() } ?: m.fileName,
                        color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    val srcTail = m.planeTailNumber?.takeIf { it.isNotBlank() }
                    if (srcTail != null) {
                        Text(
                            "currently on $srcTail",
                            color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
        }
    }

    ErrorLine(error)
    Spacer(Modifier.size(14.dp))
    PrimaryButton(label = if (busy) "Saving…" else "Create Plane", enabled = !busy) {
        busy = true
        error = null
        // Auto-attach recalled (tail-matched) manuals + user ticks + the
        // checked Phase 4 type suggestions.
        val typeIds = typeSuggestions.map { it.id }.filter { it in selectedTypeManualIds }
        val toAttach = (selectedManualIds + recalledManuals.map { it.id } + typeIds).distinct()
        val tailValue = tail.trim()
        val inspection = incomingInspection.trim()
        val typeValue = aircraftType.trim()
        scope.launch {
            when (val r = SharedStore.createPlane(tail, display, color)) {
                SharedStore.CreateResult.Success -> {
                    // createPlane re-pulled the snapshot, so the new plane is
                    // now in state — resolve its id by tail and attach picks.
                    val newPlane = SharedStore.state.value.planes
                        .firstOrNull { it.tailNumber.equals(tailValue, ignoreCase = true) }
                    if (newPlane != null) {
                        if (toAttach.isNotEmpty()) {
                            SharedStore.attachManualsToPlane(newPlane.id, newPlane.tailNumber, toAttach)
                        }
                        // Tag receiving inspection + aircraft type (preserve arrival/deadline).
                        if (inspection.isNotBlank() || typeValue.isNotBlank()) {
                            SharedStore.updatePlane(
                                newPlane.id, newPlane.tailNumber, newPlane.displayName,
                                newPlane.outlineHex ?: color,
                                newPlane.arrivalDate, newPlane.deadlineDate,
                                inspection.takeIf { it.isNotBlank() },
                                typeValue.takeIf { it.isNotBlank() }
                            )
                        }
                    }
                    onDone()
                }
                is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
            }
        }
    }
}

@Composable
private fun CreateWorkLogForm(onDone: () -> Unit) {
    val state by SharedStore.state.collectAsState()
    var planeId by remember { mutableStateOf<String?>(null) }
    var planeTail by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(HFWorkCategory.General) }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Staged rows — stack several work logs for one plane, then save the
    // whole list in one batch (mirrors macOS / Desktop bulk entry).
    var drafts by remember { mutableStateOf(listOf<SharedStore.NewWorkLogDraft>()) }
    val scope = rememberCoroutineScope()

    val pendingCurrent = title.trim().isNotBlank()
    val totalToSave = drafts.size + if (pendingCurrent) 1 else 0

    Label("Plane")
    Spacer(Modifier.size(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScrollSafe(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.planes.forEach { p ->
            val selected = p.id == planeId
            Chip(label = p.tailNumber, selected = selected) {
                planeId = p.id
                planeTail = p.tailNumber
            }
        }
    }

    if (drafts.isNotEmpty()) {
        Spacer(Modifier.size(10.dp))
        Label("Staged (${drafts.size})")
        Spacer(Modifier.size(6.dp))
        drafts.forEachIndexed { idx, d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HFColors.OnSurface.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${idx + 1}. ${d.title}",
                    color = HFColors.OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Remove",
                    color = HFColors.StatusRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { drafts = drafts.filterIndexed { i, _ -> i != idx } }
                )
            }
            Spacer(Modifier.size(6.dp))
        }
    }

    Spacer(Modifier.size(12.dp))
    FormField(label = "Title", value = title, onChange = { title = it }, placeholder = "Replace landing light")
    Spacer(Modifier.size(12.dp))
    Label("Category")
    Spacer(Modifier.size(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScrollSafe(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HFWorkCategory.values().forEach { cat ->
            Chip(label = cat.label, selected = cat == category) { category = cat }
        }
    }
    Spacer(Modifier.size(12.dp))
    FormField(
        label = "Details",
        value = details,
        onChange = { details = it },
        placeholder = "Notes, manual reference, parts needed…",
        singleLine = false
    )

    Spacer(Modifier.size(10.dp))
    // "Add another" stages the current entry and clears title/details so the
    // tech can immediately type the next one (plane + category stay put).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (pendingCurrent) HFColors.StatusGreen.copy(alpha = 0.12f)
                else HFColors.OnSurface.copy(alpha = 0.05f)
            )
            .clickable(enabled = pendingCurrent) {
                drafts = drafts + SharedStore.NewWorkLogDraft(planeId, planeTail, title.trim(), category.raw, details.trim())
                title = ""
                details = ""
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+ Add another work log to this list",
            color = if (pendingCurrent) HFColors.StatusGreen else HFColors.OnSurface.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    ErrorLine(error)
    Spacer(Modifier.size(14.dp))
    PrimaryButton(
        label = when {
            busy -> "Saving…"
            totalToSave > 1 -> "Save $totalToSave work logs"
            else -> "Create Work Log"
        },
        enabled = !busy && planeId != null && totalToSave > 0
    ) {
        busy = true
        error = null
        val all = drafts.toMutableList()
        if (pendingCurrent) {
            all += SharedStore.NewWorkLogDraft(planeId, planeTail, title.trim(), category.raw, details.trim())
        }
        scope.launch {
            when (val r = SharedStore.createWorkLogsBulk(all)) {
                SharedStore.CreateResult.Success -> onDone()
                is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
            }
        }
    }
}

@Composable
private fun InviteUserForm(onDone: () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("tech") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Lead techs can invite techs + lead techs, never admins.
    val roleOptions = if (auth.isAdmin) listOf("tech", "lead_tech", "admin") else listOf("tech", "lead_tech")
    fun roleLabel(r: String) = when (r) { "lead_tech" -> "Lead Tech"; "admin" -> "Admin"; else -> "Tech" }

    FormField(
        label = "Email",
        value = email,
        onChange = { email = it },
        placeholder = "name@shop.com",
        keyboardType = KeyboardType.Email
    )
    Spacer(Modifier.size(10.dp))
    FormField(label = "Display Name", value = name, onChange = { name = it }, placeholder = "Jane Doe")
    Spacer(Modifier.size(12.dp))
    Label("Role")
    Spacer(Modifier.size(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        roleOptions.forEach { r ->
            Chip(label = roleLabel(r), selected = role == r) { role = r }
        }
    }
    ErrorLine(error)
    Spacer(Modifier.size(14.dp))
    PrimaryButton(label = if (busy) "Sending…" else "Send Invite", enabled = !busy) {
        busy = true
        error = null
        scope.launch {
            when (val r = SharedStore.inviteEmployee(email, name, role)) {
                SharedStore.CreateResult.Success -> onDone()
                is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
            }
        }
    }
}

// ---------- Shared bits ----------

@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Label(label)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            placeholder = {
                Text(
                    placeholder,
                    color = HFColors.OnSurface.copy(alpha = 0.35f),
                    fontSize = 13.sp
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
                unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
                focusedTextColor = HFColors.OnSurface,
                unfocusedTextColor = HFColors.OnSurface,
                cursorColor = HFColors.OnSurface
            )
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = HFColors.OnSurface.copy(alpha = 0.60f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) HFColors.OnSurface.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.06f)
    val border = if (selected) HFColors.OnSurface.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.OnSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = HFColors.BrandInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorLine(message: String?) {
    if (message != null) {
        Spacer(Modifier.size(10.dp))
        Text(
            message,
            color = HFColors.StatusRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Horizontal scroll helper for chip rows — reusing rememberScrollState
 *  inside a composable requires a composable receiver. */
@Composable
private fun Modifier.horizontalScrollSafe(): Modifier {
    val scrollState = rememberScrollState()
    return this.then(Modifier.horizontalScroll(scrollState))
}

private fun parseHex(hex: String): androidx.compose.ui.graphics.Color {
    val clean = hex.removePrefix("#").takeIf { it.length == 6 } ?: "FFFFFF"
    return androidx.compose.ui.graphics.Color("FF$clean".toLong(16))
}

// Matches iOS's 16-color outline palette so the same plane is recognizable
// across platforms.
private val PALETTE = listOf(
    "#5AC8FA", "#34C759", "#FF9500", "#FF3B30",
    "#AF52DE", "#FFCC00", "#5856D6", "#FF2D92",
    "#32D74B", "#64D2FF", "#FF6482", "#BF5AF2",
    "#FFD60A", "#30D158", "#0A84FF", "#FF453A"
)

/** Curated receiving-inspection options — what a plane is coming in for. */
val INCOMING_INSPECTION_OPTIONS = listOf(
    "100-hr", "200-hr", "Annual", "Progressive / Phase", "5-year", "Pre-buy", "Other"
)
