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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFPartRequest
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors

@Composable
fun PartsToOrderHub() {
    HFPullToRefreshHost { PartsToOrderHubContent() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartsToOrderHubContent() {
    val state by SharedStore.state.collectAsState()

    var selectedUrgency by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf("all_open") }
    var statusSheetFor by remember { mutableStateOf<HFPartRequest?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val sorted = remember(state.partRequests) {
        state.partRequests.sortedWith(
            compareBy(
                { urgencyRank(it.urgency) },
                { statusRank(it.status) },
                { it.createdAt ?: "" }
            )
        )
    }

    val filtered = remember(sorted, selectedUrgency, selectedStatus) {
        sorted.filter { req ->
            val urgencyOk = selectedUrgency == null || req.urgency == selectedUrgency
            val statusOk = when (selectedStatus) {
                "all" -> true
                "all_open" -> req.status != "installed"
                else -> req.status == selectedStatus
            }
            urgencyOk && statusOk
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Urgency row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubChip(
                label = "All urgencies",
                isSelected = selectedUrgency == null,
                onClick = { selectedUrgency = null }
            )
            HubChip(
                label = "AOG / Urgent",
                accent = HFColors.StatusRed,
                isSelected = selectedUrgency == "urgentAOG",
                onClick = { selectedUrgency = "urgentAOG" }
            )
            HubChip(
                label = "Normal",
                accent = HFColors.StatusOrange,
                isSelected = selectedUrgency == "normal",
                onClick = { selectedUrgency = "normal" }
            )
            HubChip(
                label = "Low",
                accent = HFColors.StatusGreen,
                isSelected = selectedUrgency == "low",
                onClick = { selectedUrgency = "low" }
            )
        }
        Spacer(Modifier.size(8.dp))

        // Status row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubChip(label = "Open", isSelected = selectedStatus == "all_open", onClick = { selectedStatus = "all_open" })
            HubChip(label = "Requested", isSelected = selectedStatus == "requested", onClick = { selectedStatus = "requested" })
            HubChip(label = "Ordered", isSelected = selectedStatus == "ordered", onClick = { selectedStatus = "ordered" })
            HubChip(label = "Received", isSelected = selectedStatus == "received", onClick = { selectedStatus = "received" })
            HubChip(label = "Installed", isSelected = selectedStatus == "installed", onClick = { selectedStatus = "installed" })
            HubChip(label = "All", isSelected = selectedStatus == "all", onClick = { selectedStatus = "all" })
        }
        Spacer(Modifier.size(12.dp))

        Text(
            text = "${filtered.size} of ${state.partRequests.size}",
            color = HFColors.OnSurface.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(10.dp))

        if (filtered.isEmpty()) {
            IOSPlaceholderPanel(
                message = if (state.partRequests.isEmpty())
                    "No parts requests yet."
                else "No parts match the current filter."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { request ->
                    PartRequestCard(
                        request = request,
                        onTapStatus = { statusSheetFor = request }
                    )
                }
            }
        }
    }

    statusSheetFor?.let { req ->
        ModalBottomSheet(
            onDismissRequest = { statusSheetFor = null },
            sheetState = sheetState,
            containerColor = HFColors.Surface,
            contentColor = HFColors.OnSurface
        ) {
            StatusPickerSheet(
                current = req.status,
                onPick = {
                    SharedStore.updatePartRequestStatus(req.id, it)
                    statusSheetFor = null
                }
            )
        }
    }
}

@Composable
private fun PartRequestCard(
    request: HFPartRequest,
    onTapStatus: () -> Unit
) {
    val urgency = urgencyPresentation(request.urgency)
    val status = statusPresentation(request.status)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, urgency.color.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = request.title.takeIf { it.isNotBlank() } ?: request.requestedPart,
                    color = HFColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.size(4.dp))
                if (request.requestedPart.isNotBlank() && request.requestedPart != request.title) {
                    Text(
                        text = "PN ${request.requestedPart}",
                        color = HFColors.OnSurface.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!request.planeTailNumber.isNullOrBlank()) {
                        MetaPill(request.planeTailNumber, HFColors.OnSurface.copy(alpha = 0.55f))
                        Spacer(Modifier.width(6.dp))
                    }
                    UrgencyPill(urgency.label, urgency.color)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTapStatus)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                StatusBadge(status.label, status.color)
            }
        }

        if (request.notes.isNotBlank()) {
            Spacer(Modifier.size(10.dp))
            Text(
                text = request.notes,
                color = HFColors.OnSurface.copy(alpha = 0.70f),
                fontSize = 12.sp,
                maxLines = 3
            )
        }
    }
}

// ---------- helpers ----------

@Composable
private fun HubChip(
    label: String,
    accent: Color = HFColors.OnSurface,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.06f)
    val fg = if (isSelected) HFColors.BrandInk else HFColors.OnSurface
    val border = if (isSelected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.10f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UrgencyPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPickerSheet(current: String, onPick: (String) -> Unit) {
    val options = listOf("requested", "ordered", "received", "installed")
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            "Update Status".uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(12.dp))
        options.forEach { option ->
            val preset = statusPresentation(option)
            val isCurrent = option == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrent) preset.color.copy(alpha = 0.14f)
                        else HFColors.OnSurface.copy(alpha = 0.04f)
                    )
                    .border(
                        1.dp,
                        if (isCurrent) preset.color.copy(alpha = 0.45f) else HFColors.OnSurface.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onPick(option) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(preset.color))
                Spacer(Modifier.size(10.dp))
                Text(
                    preset.label,
                    color = if (isCurrent) preset.color else HFColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.size(8.dp))
        }
        Spacer(Modifier.size(12.dp))
    }
}

private data class PartPreset(val label: String, val color: Color)

private fun urgencyPresentation(raw: String): PartPreset = when (raw) {
    "urgentAOG" -> PartPreset("AOG / URGENT", HFColors.StatusRed)
    "normal" -> PartPreset("NORMAL", HFColors.StatusOrange)
    "low" -> PartPreset("LOW", HFColors.StatusGreen)
    else -> PartPreset(raw.uppercase(), HFColors.OnSurface)
}

private fun statusPresentation(raw: String): PartPreset = when (raw) {
    "requested" -> PartPreset("Requested", HFColors.StatusBlue)
    "ordered" -> PartPreset("Ordered", HFColors.StatusYellow)
    "received" -> PartPreset("Received", HFColors.StatusCyan)
    "installed" -> PartPreset("Installed", HFColors.StatusGreen)
    else -> PartPreset(raw.replaceFirstChar { it.titlecase() }, HFColors.OnSurface)
}

private fun urgencyRank(raw: String): Int = when (raw) {
    "urgentAOG" -> 0
    "normal" -> 1
    "low" -> 2
    else -> 3
}

private fun statusRank(raw: String): Int = when (raw) {
    "requested" -> 0
    "ordered" -> 1
    "received" -> 2
    "installed" -> 3
    else -> 4
}
