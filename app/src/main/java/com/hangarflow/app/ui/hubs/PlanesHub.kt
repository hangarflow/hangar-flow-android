package com.hangarflow.app.ui.hubs

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors

/**
 * Planes hub sheet. Matches iOS `IOSPlanesHubView` — a 2-column grid of
 * `PlaneCard`s. Each card has an airplane icon, tail number, display name,
 * and a colored outline matching the plane's `outline_hex` (same accent
 * the admin picks on the Mac).
 */
@Composable
fun PlanesHub(onOpenWorkLog: (com.hangarflow.app.data.model.HFWorkLog) -> Unit) {
    val state by SharedStore.state.collectAsState()
    var selectedPlaneId by remember { mutableStateOf<String?>(null) }

    val selectedPlane = state.planes.firstOrNull { it.id == selectedPlaneId }
    if (selectedPlane != null) {
        PlaneDetailView(
            plane = selectedPlane,
            onBack = { selectedPlaneId = null },
            onOpenWorkLog = onOpenWorkLog
        )
        return
    }

    com.hangarflow.app.ui.admin.AdminFabOverlay(
        mode = com.hangarflow.app.ui.admin.AdminCreateMode.Plane
    ) {
        HFPullToRefreshHost {
            PlanesHubContent(onSelectPlane = { selectedPlaneId = it.id })
        }
    }
}

@Composable
private fun PlanesHubContent(onSelectPlane: (HFPlane) -> Unit) {
    val state by SharedStore.state.collectAsState()
    var showArchived by remember { mutableStateOf(false) }

    if (state.planes.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No planes yet",
                color = HFColors.OnSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "An admin can add aircraft from the desktop — they'll show up here instantly.",
                color = HFColors.OnSurface.copy(alpha = 0.68f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    val archivedCount = state.planes.count { it.isArchived }
    val visiblePlanes = state.planes.filter { showArchived || !it.isArchived }
    // Chunk into 2-column rows so the grid matches iOS `LazyVGrid spacing:14`.
    val rows = visiblePlanes.chunked(2)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (archivedCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (showArchived) HFColors.StatusYellow.copy(alpha = 0.16f)
                            else HFColors.OnSurface.copy(alpha = 0.06f)
                        )
                        .clickable { showArchived = !showArchived }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showArchived) "Showing archived ($archivedCount)" else "Show archived ($archivedCount)",
                        color = if (showArchived) HFColors.StatusYellow else HFColors.OnSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        items(rows) { rowPair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowPair.forEach { plane ->
                    IOSPlaneCard(
                        plane = plane,
                        onClick = { onSelectPlane(plane) },
                        modifier = Modifier.weight(1f),
                        workLogCount = state.workLogs.count { it.planeId == plane.id },
                        openSquawkCount = state.squawks.count {
                            it.planeId == plane.id &&
                            (it.status == "open" || it.status == "inProgress" || it.status == "waitingOnParts")
                        }
                    )
                }
                if (rowPair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Port of iOS `PlaneCard` (ContentView.swift ~6340). Airplane icon in
 * top-left, chevron top-right, bold tail, muted display name, rounded
 * 18dp card with a 2dp accent-colored border driven by `outline_hex`.
 */
@Composable
private fun IOSPlaneCard(
    plane: HFPlane,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    workLogCount: Int = 0,
    openSquawkCount: Int = 0
) {
    val outline = runCatching {
        plane.outlineHex
            ?.removePrefix("#")
            ?.takeIf { it.length == 6 }
            ?.let { Color("FF$it".toLong(16)) }
    }.getOrNull() ?: HFColors.OnSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(2.dp, outline.copy(alpha = 0.95f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Flight,
                contentDescription = null,
                tint = HFColors.OnSurface,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = HFColors.OnSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            plane.tailNumber,
            color = HFColors.OnSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        if (plane.displayName.isNotBlank()) {
            Text(
                plane.displayName,
                color = HFColors.OnSurface.copy(alpha = 0.70f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$workLogCount work log${if (workLogCount == 1) "" else "s"}",
                color = HFColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (openSquawkCount > 0) {
                Text(
                    "$openSquawkCount squawk${if (openSquawkCount == 1) "" else "s"}",
                    color = HFColors.StatusOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HFColors.StatusOrange.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

