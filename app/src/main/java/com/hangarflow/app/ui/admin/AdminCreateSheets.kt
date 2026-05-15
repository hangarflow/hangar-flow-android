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
    var tail by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PALETTE.first()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
    ErrorLine(error)
    Spacer(Modifier.size(14.dp))
    PrimaryButton(label = if (busy) "Saving…" else "Create Plane", enabled = !busy) {
        busy = true
        error = null
        scope.launch {
            when (val r = SharedStore.createPlane(tail, display, color)) {
                SharedStore.CreateResult.Success -> onDone()
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
    val scope = rememberCoroutineScope()

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
    ErrorLine(error)
    Spacer(Modifier.size(14.dp))
    PrimaryButton(label = if (busy) "Saving…" else "Create Work Log", enabled = !busy) {
        busy = true
        error = null
        scope.launch {
            when (val r = SharedStore.createWorkLog(planeId, planeTail, title, category.raw, details)) {
                SharedStore.CreateResult.Success -> onDone()
                is SharedStore.CreateResult.Error -> { error = r.message; busy = false }
            }
        }
    }
}

@Composable
private fun InviteUserForm(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("tech") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        Chip(label = "Tech", selected = role == "tech") { role = "tech" }
        Chip(label = "Admin", selected = role == "admin") { role = "admin" }
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
