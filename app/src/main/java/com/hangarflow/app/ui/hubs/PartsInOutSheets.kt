package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.model.HFPartMovement
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * The touch answer to the Desktop's right-click menu.
 *
 * Every action a card can take, as full-width rows big enough for a thumb in
 * a hangar. A long-press menu would have hidden all of this behind a gesture
 * that nothing on screen advertises.
 */
@Composable
internal fun MovementActionsSheet(
    movement: HFPartMovement,
    canManage: Boolean,
    onEdit: () -> Unit,
    onAction: (String) -> Unit,
    onClose: () -> Unit
) {
    val m = movement
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
        Text(
            m.partNumber.ifBlank { m.description }.ifBlank { "Untitled part" },
            color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        Text(
            listOfNotNull(
                m.kindLabel,
                m.vendorName.takeIf { it.isNotBlank() },
                m.planeTailNumber?.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            color = HFColors.OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        SheetAction("Edit details", Icons.Outlined.Edit, HFColors.StatusBlue, onEdit)

        if (canManage) {
            when {
                m.isCore && m.status == "awaiting" ->
                    SheetAction("Mark shipped", Icons.Outlined.LocalShipping,
                        HFColors.StatusBlue) { onAction("in_transit") }
                m.isCore && m.status == "in_transit" ->
                    SheetAction("Vendor has it", Icons.Outlined.LocalShipping,
                        HFColors.StatusBlue) { onAction("delivered") }
                m.isCore && m.status == "delivered" -> {
                    SheetAction("Deposit refunded", Icons.Outlined.Inventory2,
                        HFColors.StatusGreen) { onAction("refunded") }
                    SheetAction("Core rejected", Icons.Outlined.Inventory2,
                        HFColors.StatusOrange) { onAction("rejected") }
                }
                m.isInbound && m.status == "in_transit" ->
                    SheetAction("Mark received", Icons.Outlined.Inventory2,
                        HFColors.StatusGreen) { onAction("received") }
                // The decision the parts room actually makes when the box is
                // opened. "Add to inventory" is the only thing that moves
                // stock; fitted-on-arrival deliberately doesn't, or the count
                // would include parts already on the aircraft.
                m.isInbound && m.status == "received" -> {
                    SheetAction("Add to inventory", Icons.Outlined.Inventory2,
                        HFColors.StatusGreen) { onAction("__shelf") }
                    SheetAction(
                        m.planeTailNumber?.takeIf { it.isNotBlank() }
                            ?.let { "Fitted straight to $it" } ?: "Fitted on arrival",
                        Icons.Outlined.LocalShipping, HFColors.StatusBlue
                    ) { onAction("__aircraft") }
                }
            }
            SheetAction("Delete", Icons.Outlined.Delete, HFColors.StatusRed) {
                onAction("__delete")
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClose).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Cancel", color = HFColors.OnSurfaceMuted, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, color = HFColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceivePartSheet(onDismiss: () -> Unit) {
    val state by SharedStore.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var partNumber by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var vendor by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("") }
    var tracking by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var coreOwed by remember { mutableStateOf(false) }
    var coreDeposit by remember { mutableStateOf("") }
    var planeId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val plane = state.planes.firstOrNull { it.id == planeId }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = HFColors.Surface,
        contentColor = HFColors.OnSurface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Receive a part", color = HFColors.OnSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Field(partNumber, { partNumber = it }, "Part number")
            Field(description, { description = it }, "What it is")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(serial, { serial = it }, "Serial") }
                Box(Modifier.width(90.dp)) {
                    Field(qty, { qty = it.filter(Char::isDigit) }, "Qty")
                }
            }
            Field(vendor, { vendor = it }, "Vendor")

            // Aircraft picker — plain chips rather than a dropdown, because a
            // dropdown in a bottom sheet on a tablet is a fight with the IME.
            Text("For which aircraft", color = HFColors.OnSurfaceMuted, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PickRow("Not for a specific aircraft", planeId == null) { planeId = null }
                state.planes.take(12).forEach { p ->
                    PickRow(p.tailNumber, planeId == p.id) { planeId = p.id }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(carrier, { carrier = it }, "Carrier") }
                Box(Modifier.weight(1.6f)) { Field(tracking, { tracking = it }, "Tracking number") }
            }
            Field(condition, { condition = it }, "Condition — as written on the tag")

            Text("CORE", color = HFColors.OnSurfaceMuted, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            PickRow("Owes a core back", coreOwed) { coreOwed = !coreOwed }
            if (coreOwed) {
                Field(coreDeposit, { coreDeposit = it.filter { c -> c.isDigit() || c == '.' } },
                    "Core deposit ($) — leave blank if none charged")
            }

            Field(notes, { notes = it }, "Notes")
            err?.let { Text(it, color = HFColors.StatusRed, fontSize = 12.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !busy) { onDismiss() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = HFColors.OnSurfaceMuted, fontSize = 14.sp) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(HFColors.StatusGreen.copy(alpha = 0.16f))
                        .clickable(enabled = !busy) {
                            busy = true; err = null
                            scope.launch {
                                val r = SharedStore.receivePart(
                                    partNumber = partNumber, description = description,
                                    serialNumber = serial.trim().ifBlank { null },
                                    quantity = qty.toIntOrNull() ?: 1,
                                    vendorName = vendor, planeId = planeId,
                                    planeTailNumber = plane?.tailNumber,
                                    carrier = carrier.trim().ifBlank { null },
                                    trackingNumber = tracking.trim().ifBlank { null },
                                    condition = condition.trim().ifBlank { null },
                                    coreOwed = coreOwed,
                                    coreDepositCents = coreDeposit.trim().toDoubleOrNull()
                                        ?.let { (it * 100).toLong() }?.takeIf { it > 0 },
                                    coreWindowDays = 30,
                                    notes = notes
                                )
                                when (r) {
                                    SharedStore.CreateResult.Success -> onDismiss()
                                    is SharedStore.CreateResult.Error -> {
                                        err = r.message; busy = false
                                    }
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (busy) "Saving…" else "Log it", color = HFColors.StatusGreen,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------

/**
 * Correct a movement after the fact.
 *
 * The field this sheet exists for is **Serial**. When a replacement lands and
 * the shop owes a core, the core row opens with a blank serial on purpose —
 * the unit that comes off the aircraft is a different physical part with a
 * different serial, and at that moment nobody has pulled it yet. This is
 * where it gets filled in, off the data plate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditMovementSheet(movement: HFPartMovement, onDismiss: () -> Unit) {
    val state by SharedStore.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var partNumber by remember { mutableStateOf(movement.partNumber) }
    var description by remember { mutableStateOf(movement.description) }
    var serial by remember { mutableStateOf(movement.serialNumber ?: "") }
    var qty by remember { mutableStateOf(movement.quantity.toString()) }
    var vendor by remember { mutableStateOf(movement.vendorName) }
    var carrier by remember { mutableStateOf(movement.carrier ?: "") }
    var tracking by remember { mutableStateOf(movement.trackingNumber ?: "") }
    var condition by remember { mutableStateOf(movement.condition ?: "") }
    var deposit by remember {
        mutableStateOf(movement.coreDepositCents?.let { (it / 100.0).toString() } ?: "")
    }
    var dueBack by remember { mutableStateOf(movement.coreDueBackBy?.take(10) ?: "") }
    var notes by remember { mutableStateOf(movement.notes) }

    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var identifying by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf<HFCloudSyncService.PartIdentifyResult?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    val aircraftType = movement.planeId
        ?.let { id -> state.planes.firstOrNull { it.id == id }?.model }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = HFColors.Surface,
        contentColor = HFColors.OnSurface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (movement.isCore) "Edit core going out" else "Edit part",
                color = HFColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(partNumber, { partNumber = it }, "Part number") }
                if (partNumber.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(HFColors.StatusBlue.copy(alpha = 0.14f))
                            .clickable(enabled = !identifying) {
                                identifying = true; lookup = null; lookupError = null
                                scope.launch {
                                    SharedStore.identifyPart(partNumber, aircraftType)
                                        .onSuccess { r ->
                                            lookup = r
                                            // Only fill blanks. Overwriting what a
                                            // tech typed is how bad data gets in.
                                            if (description.isBlank()) {
                                                description = r.description.orEmpty()
                                            }
                                            if (vendor.isBlank()) {
                                                vendor = r.manufacturer.orEmpty()
                                            }
                                            if (deposit.isBlank() && movement.isCore) {
                                                r.typicalCoreValueUsd?.let { deposit = it.toString() }
                                            }
                                        }
                                        .onFailure { lookupError = "Couldn't reach the lookup." }
                                    identifying = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(if (identifying) "…" else "Look up", color = HFColors.StatusBlue,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Where the answer came from, said plainly. "Your own shelf says
            // this" and "a model thinks this" must never look the same.
            lookup?.let { r ->
                val accent = when {
                    r.isGrounded -> HFColors.StatusGreen
                    r.source == "ai" && r.description != null -> HFColors.StatusOrange
                    else -> HFColors.OnSurfaceMuted
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(r.sourceLabel, color = accent, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                    r.reference?.let {
                        Text(it, color = HFColors.OnSurface.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                    r.note?.let { Text(it, color = HFColors.OnSurfaceMuted, fontSize = 11.sp) }
                }
            }
            lookupError?.let { Text(it, color = HFColors.StatusOrange, fontSize = 11.sp) }

            Field(description, { description = it }, "What it is")

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    Field(
                        serial, { serial = it },
                        if (movement.isCore) "Serial — off the unit you pulled" else "Serial"
                    )
                }
                Box(Modifier.width(90.dp)) {
                    Field(qty, { qty = it.filter(Char::isDigit) }, "Qty")
                }
            }

            // Said out loud, because the whole point of this sheet is that the
            // two serials are not the same number.
            if (movement.isCore) {
                Text(
                    "The core going back is the unit that came off the aircraft — a " +
                        "different serial from the one that arrived. Read it off the data " +
                        "plate; nothing fills this in for you.",
                    color = HFColors.OnSurfaceMuted, fontSize = 11.sp
                )
            }

            Field(vendor, { vendor = it }, "Vendor")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(carrier, { carrier = it }, "Carrier") }
                Box(Modifier.weight(1.6f)) { Field(tracking, { tracking = it }, "Tracking number") }
            }
            Field(condition, { condition = it }, "Condition — as written on the tag")

            if (movement.isCore) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        Field(deposit, { deposit = it.filter { c -> c.isDigit() || c == '.' } },
                            "Core deposit ($)")
                    }
                    Box(Modifier.weight(1.3f)) {
                        Field(dueBack, { dueBack = it }, "Due back (YYYY-MM-DD)")
                    }
                }
            }

            Field(notes, { notes = it }, "Notes")
            err?.let { Text(it, color = HFColors.StatusRed, fontSize = 12.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !busy) { onDismiss() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = HFColors.OnSurfaceMuted, fontSize = 14.sp) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(HFColors.StatusGreen.copy(alpha = 0.16f))
                        .clickable(enabled = !busy) {
                            busy = true; err = null
                            scope.launch {
                                val r = SharedStore.editPartMovement(
                                    movementId = movement.id,
                                    partNumber = partNumber, description = description,
                                    serialNumber = serial.trim().ifBlank { null },
                                    quantity = qty.toIntOrNull() ?: 1,
                                    vendorName = vendor,
                                    carrier = carrier.trim().ifBlank { null },
                                    trackingNumber = tracking.trim().ifBlank { null },
                                    condition = condition.trim().ifBlank { null },
                                    coreDueBackBy = dueBack.trim().ifBlank { null },
                                    coreDepositCents = deposit.trim().toDoubleOrNull()
                                        ?.let { (it * 100).toLong() }?.takeIf { it > 0 },
                                    notes = notes
                                )
                                when (r) {
                                    SharedStore.CreateResult.Success -> onDismiss()
                                    is SharedStore.CreateResult.Error -> {
                                        err = r.message; busy = false
                                    }
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (busy) "Saving…" else "Save", color = HFColors.StatusGreen,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- small pieces ---------------------------------------------------------

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = HFColors.SurfaceElevated,
            unfocusedContainerColor = HFColors.SurfaceElevated,
            focusedBorderColor = HFColors.OutlineStrong,
            unfocusedBorderColor = HFColors.OutlineSubtle,
            focusedTextColor = HFColors.OnSurface,
            unfocusedTextColor = HFColors.OnSurface,
            cursorColor = HFColors.OnSurface,
            focusedLabelColor = HFColors.OnSurfaceMuted,
            unfocusedLabelColor = HFColors.OnSurfaceMuted
        )
    )
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) HFColors.StatusBlue.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.03f)
            )
            .border(
                1.dp,
                if (selected) HFColors.StatusBlue.copy(alpha = 0.40f) else HFColors.OutlineSubtle,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) HFColors.StatusBlue else HFColors.OnSurface,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
