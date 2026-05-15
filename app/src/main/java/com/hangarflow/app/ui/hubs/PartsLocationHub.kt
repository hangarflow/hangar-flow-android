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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.AutoMirrored
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateListOf
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
import com.hangarflow.app.data.model.HFPartLocation
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

// ---------- stock status ----------

private enum class StockStatus(val raw: String, val label: String, val color: Color) {
    Ok("ok", "In Stock", HFColors.StatusGreen),
    Low("low", "Low", HFColors.StatusYellow),
    Urgent("urgent", "Urgent", HFColors.StatusRed),
    OrderMore("order_more", "Order More", HFColors.StatusOrange);

    companion object {
        fun of(raw: String): StockStatus =
            entries.firstOrNull { it.raw == raw.lowercase() } ?: Ok
    }
}

// ---------- filter state ----------

private data class InventoryFilters(
    val planeId: String? = null,
    val location: String? = null,
    val status: StockStatus? = null,
    val minQuantity: Int? = null,
    val query: String = ""
)

// ---------- hub entry ----------

@Composable
fun PartsLocationHub() {
    HFPullToRefreshHost { PartsLocationHubContent() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartsLocationHubContent() {
    val state by SharedStore.state.collectAsState()
    var filters by remember { mutableStateOf(InventoryFilters()) }
    var editing by remember { mutableStateOf<HFPartLocation?>(null) }
    var createOpen by remember { mutableStateOf(false) }

    val filtered = remember(state.partLocations, filters) {
        val q = filters.query.trim().lowercase()
        state.partLocations.filter { row ->
            val matchesQuery = q.isEmpty() ||
                row.partName.lowercase().contains(q) ||
                row.partNumber.lowercase().contains(q) ||
                row.serialNumber.lowercase().contains(q) ||
                row.location.lowercase().contains(q) ||
                row.notes.lowercase().contains(q)
            val matchesPlane = filters.planeId == null || filters.planeId in row.planeIds
            val matchesLocation = filters.location == null || row.location == filters.location
            val matchesStatus = filters.status == null ||
                StockStatus.of(row.stockStatus) == filters.status
            val matchesQty = filters.minQuantity == null || row.quantity >= filters.minQuantity!!
            matchesQuery && matchesPlane && matchesLocation && matchesStatus && matchesQty
        }
    }

    val summary = remember(state.partLocations) {
        InventorySummary(
            totalParts = state.partLocations.size,
            areas = state.partLocations.map { it.location.trim() }.filter { it.isNotBlank() }.distinct().size,
            lowOrUrgent = state.partLocations.count {
                val s = StockStatus.of(it.stockStatus)
                s == StockStatus.Low || s == StockStatus.Urgent || s == StockStatus.OrderMore
            },
            onHand = state.partLocations.sumOf { it.quantity }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search is first, like the iPad — sits right under the hub
        // header so it's always reachable on a full-day inventory pass.
        item {
            OutlinedTextField(
                value = filters.query,
                onValueChange = { filters = filters.copy(query = it) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, tint = HFColors.OnSurface.copy(alpha = 0.55f))
                },
                placeholder = {
                    Text(
                        "Search parts, P/N, S/N, serial, location…",
                        color = HFColors.OnSurface.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = inventoryFieldColors()
            )
        }

        item { LiveSummary(summary) }

        item {
            AddPartButton(onClick = { createOpen = true })
        }

        item {
            FilterRows(
                planes = state.planes,
                allLocations = state.partLocations.map { it.location.trim() }
                    .filter { it.isNotBlank() }.distinct().sorted(),
                filters = filters,
                onChange = { filters = it }
            )
        }

        item {
            Text(
                text = if (filters.isBlank()) "${filtered.size} parts"
                    else "${filtered.size} of ${state.partLocations.size} parts",
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.partLocations.isEmpty()) {
            item {
                IOSPlaceholderPanel(
                    message = "No parts logged yet. Tap Add Part above to start tracking inventory — every tech in the shop will see the same list."
                )
            }
        } else if (filtered.isEmpty()) {
            item { IOSPlaceholderPanel(message = "No parts match the current filters.") }
        } else {
            items(filtered, key = { it.id }) { row ->
                PartLocationRow(
                    row = row,
                    planes = state.planes,
                    onClick = { editing = row }
                )
            }
        }
    }

    if (createOpen) {
        PartLocationSheet(
            existing = null,
            planes = state.planes,
            onDismiss = { createOpen = false }
        )
    }
    editing?.let { row ->
        PartLocationSheet(
            existing = row,
            planes = state.planes,
            onDismiss = { editing = null }
        )
    }
}

private fun InventoryFilters.isBlank(): Boolean =
    planeId == null && location == null && status == null &&
        minQuantity == null && query.isBlank()

// ---------- live summary ----------

private data class InventorySummary(
    val totalParts: Int,
    val areas: Int,
    val lowOrUrgent: Int,
    val onHand: Int
)

/** iPad-style three-across stat strip. Total Parts moves to the filter
 *  count line below since the chip row already shows it. */
@Composable
private fun LiveSummary(s: InventorySummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile("AREAS", s.areas.toString(), HFColors.StatusCyan, Modifier.weight(1f))
        SummaryTile("LOW / URGENT", s.lowOrUrgent.toString(), HFColors.StatusRed, Modifier.weight(1f))
        SummaryTile("ON HAND", s.onHand.toString(), HFColors.StatusGreen, Modifier.weight(1f))
    }
}

/** Monochrome "+ Add Part" pill. White text + add icon on a subtle
 *  dark background so it reads as an action without overpowering the
 *  stat tiles above. */
@Composable
private fun AddPartButton(onClick: () -> Unit) {
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
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = HFColors.OnSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Add Part",
            color = HFColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
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

// ---------- filter row ----------

@Composable
private fun FilterRows(
    planes: List<HFPlane>,
    allLocations: List<String>,
    filters: InventoryFilters,
    onChange: (InventoryFilters) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Plane dropdown — plane icon + "All Planes" / tail number + ▾
        FilterDropdown(
            icon = Icons.Rounded.Flight,
            activeLabel = filters.planeId?.let { id -> planes.firstOrNull { it.id == id }?.tailNumber }
                ?: "All Planes",
            active = filters.planeId != null,
            options = buildList {
                add(DropdownOption("All Planes", null))
                planes.forEach { p -> add(DropdownOption(p.tailNumber, p.id)) }
            },
            onSelect = { onChange(filters.copy(planeId = it as String?)) }
        )

        FilterDropdown(
            icon = Icons.Outlined.Place,
            activeLabel = filters.location ?: "All Areas",
            active = filters.location != null,
            options = buildList {
                add(DropdownOption("All Areas", null))
                allLocations.forEach { add(DropdownOption(it, it)) }
            },
            onSelect = { onChange(filters.copy(location = it as String?)) }
        )

        FilterDropdown(
            icon = Icons.Outlined.Inventory2,
            activeLabel = filters.status?.label ?: "All Stock",
            active = filters.status != null,
            accent = filters.status?.color,
            options = buildList {
                add(DropdownOption("All Stock", null))
                StockStatus.entries.forEach { s -> add(DropdownOption(s.label, s, color = s.color)) }
            },
            onSelect = { onChange(filters.copy(status = it as StockStatus?)) }
        )

        FilterDropdown(
            icon = Icons.Outlined.Numbers,
            activeLabel = filters.minQuantity?.let { "≥ $it" } ?: "Min Qty",
            active = filters.minQuantity != null,
            options = listOf(
                DropdownOption("Any quantity", null),
                DropdownOption("≥ 1", 1),
                DropdownOption("≥ 5", 5),
                DropdownOption("≥ 10", 10),
                DropdownOption("≥ 25", 25),
                DropdownOption("≥ 50", 50),
                DropdownOption("≥ 100", 100)
            ),
            onSelect = { onChange(filters.copy(minQuantity = it as Int?)) }
        )
    }
}

private data class DropdownOption(
    val label: String,
    val value: Any?,
    val color: Color? = null
)

@Composable
private fun FilterDropdown(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeLabel: String,
    active: Boolean,
    options: List<DropdownOption>,
    accent: Color? = null,
    onSelect: (Any?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = when {
        active && accent != null -> accent.copy(alpha = 0.55f)
        active -> HFColors.OnSurface.copy(alpha = 0.45f)
        else -> HFColors.OnSurface.copy(alpha = 0.20f)
    }
    val fg = accent?.takeIf { active } ?: HFColors.OnSurface
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.06f))
                .border(1.dp, borderColor, RoundedCornerShape(100.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(activeLabel, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(4.dp))
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            opt.label,
                            color = opt.color ?: HFColors.OnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    onClick = {
                        onSelect(opt.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterScrollRow(content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@Composable
private fun StatusPill(
    label: String,
    color: Color = HFColors.OnSurface,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) color.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.06f)
    val fg = if (active) color else HFColors.OnSurface
    val border = if (active) color.copy(alpha = 0.55f) else HFColors.OnSurface.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- list row ----------

@Composable
private fun PartLocationRow(
    row: HFPartLocation,
    planes: List<HFPlane>,
    onClick: () -> Unit
) {
    val status = StockStatus.of(row.stockStatus)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, status.color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.partName.ifBlank { "Untitled part" },
                color = HFColors.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            StockBadge(status)
            Spacer(Modifier.size(8.dp))
            QuantityBadge(row.quantity)
        }
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.partNumber.isNotBlank()) {
                InlineTag("PN ${row.partNumber}", HFColors.StatusCyan)
                Spacer(Modifier.size(6.dp))
            }
            if (row.serialNumber.isNotBlank()) {
                InlineTag("SN ${row.serialNumber}", HFColors.StatusBlue)
                Spacer(Modifier.size(6.dp))
            }
            if (row.location.isNotBlank()) {
                Text(
                    row.location,
                    color = HFColors.OnSurface.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (row.planeIds.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            val tails = row.planeIds.mapNotNull { id -> planes.firstOrNull { it.id == id }?.tailNumber }
            if (tails.isNotEmpty()) {
                Text(
                    "For: ${tails.joinToString(", ")}",
                    color = HFColors.OnSurface.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (row.notes.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(
                row.notes,
                color = HFColors.OnSurface.copy(alpha = 0.62f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3
            )
        }
        // Vendor block — only shown when at least one vendor field is set.
        // These columns came over from the iOS hf_inventory_parts schema
        // when we unified onto hf_part_locations.
        val hasVendor = !row.vendorName.isNullOrBlank() ||
            !row.vendorPhone.isNullOrBlank() ||
            !row.vendorWebsite.isNullOrBlank()
        if (hasVendor) {
            Spacer(Modifier.size(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.vendorName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Vendor: $it",
                        color = HFColors.OnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                row.vendorPhone?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Phone: $it",
                        color = HFColors.OnSurface.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                row.vendorWebsite?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = HFColors.StatusBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (row.updatedByUserName.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(
                "Updated by ${row.updatedByUserName}",
                color = HFColors.OnSurface.copy(alpha = 0.40f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StockBadge(status: StockStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(status.color.copy(alpha = 0.18f))
            .border(1.dp, status.color.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status.label, color = status.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuantityBadge(q: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("× $q", color = HFColors.OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InlineTag(text: String, color: Color) {
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
private fun PartLocationSheet(
    existing: HFPartLocation?,
    planes: List<HFPlane>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var partName by remember { mutableStateOf(existing?.partName ?: "") }
    var partNumber by remember { mutableStateOf(existing?.partNumber ?: "") }
    var serialNumber by remember { mutableStateOf(existing?.serialNumber ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var quantity by remember { mutableStateOf((existing?.quantity ?: 1).toString()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var status by remember { mutableStateOf(StockStatus.of(existing?.stockStatus ?: "ok")) }
    val selectedPlaneIds = remember {
        mutableStateListOf<String>().apply { existing?.planeIds?.let { addAll(it) } }
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (existing == null) "Add Part" else "Edit Part",
                    color = HFColors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (existing != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(HFColors.StatusRed.copy(alpha = 0.15f))
                            .border(1.dp, HFColors.StatusRed.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                            .clickable {
                                SharedStore.deletePartLocation(existing.id)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Delete, null, tint = HFColors.StatusRed, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Delete", color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            InventoryField(label = "Part Name", value = partName, onChange = { partName = it }, placeholder = "Brake pad")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InventoryField(
                    label = "Part Number (P/N)",
                    value = partNumber,
                    onChange = { partNumber = it },
                    placeholder = "123-456",
                    modifier = Modifier.weight(1f)
                )
                InventoryField(
                    label = "Serial Number (S/N)",
                    value = serialNumber,
                    onChange = { serialNumber = it },
                    placeholder = "SN-0001",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InventoryField(
                    label = "Location",
                    value = location,
                    onChange = { location = it },
                    placeholder = "Shelf B-3",
                    modifier = Modifier.weight(1.4f)
                )
                InventoryField(
                    label = "Quantity",
                    value = quantity,
                    onChange = { s -> quantity = s.filter { it.isDigit() }.take(5) },
                    placeholder = "1",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("Stock Status")
                FilterScrollRow {
                    StockStatus.entries.forEach { s ->
                        StatusPill(label = s.label, color = s.color, active = status == s) {
                            status = s
                        }
                    }
                }
            }

            if (planes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Planes this part goes to")
                    FilterScrollRow {
                        planes.forEach { p ->
                            val active = p.id in selectedPlaneIds
                            StatusPill(label = p.tailNumber, active = active) {
                                if (active) selectedPlaneIds.remove(p.id)
                                else selectedPlaneIds.add(p.id)
                            }
                        }
                    }
                    Text(
                        "Leave empty for shop-wide parts.",
                        color = HFColors.OnSurface.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            InventoryField(
                label = "Notes",
                value = notes,
                onChange = { notes = it },
                placeholder = "Condition, lot, or context",
                singleLine = false
            )

            if (error != null) {
                Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (busy) HFColors.OnSurface.copy(alpha = 0.10f) else HFColors.OnSurface)
                    .clickable(enabled = !busy) {
                        busy = true
                        error = null
                        scope.launch {
                            val result = SharedStore.savePartLocation(
                                existingId = existing?.id,
                                partName = partName,
                                partNumber = partNumber,
                                serialNumber = serialNumber,
                                location = location,
                                quantity = quantity.toIntOrNull() ?: 1,
                                stockStatus = status.raw,
                                planeIds = selectedPlaneIds.toList(),
                                notes = notes
                            )
                            when (result) {
                                SharedStore.CreateResult.Success -> onDismiss()
                                is SharedStore.CreateResult.Error -> {
                                    error = result.message
                                    busy = false
                                }
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "Saving…" else if (existing == null) "Save Part" else "Update Part",
                    color = HFColors.BrandInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.size(28.dp))
        }
    }
}

// ---------- shared field bits ----------

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
private fun InventoryField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Label(label)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            placeholder = {
                Text(placeholder, color = HFColors.OnSurface.copy(alpha = 0.35f), fontSize = 13.sp)
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            colors = inventoryFieldColors()
        )
    }
}

@Composable
private fun inventoryFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
    focusedTextColor = HFColors.OnSurface,
    unfocusedTextColor = HFColors.OnSurface,
    cursorColor = HFColors.OnSurface
)
