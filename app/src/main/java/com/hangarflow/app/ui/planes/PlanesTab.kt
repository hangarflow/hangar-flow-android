package com.hangarflow.app.ui.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.ui.theme.HFColors

@Composable
fun PlanesTab() {
    val state by SharedStore.state.collectAsState()

    when {
        state.loading && state.planes.isEmpty() -> LoadingCenter()
        state.planes.isEmpty() -> EmptyPlanes(error = state.error)
        else -> PlaneList(planes = state.planes, workLogCountByPlane = state.workLogs
            .groupingBy { it.planeId ?: "" }
            .eachCount()
        )
    }
}

@Composable
private fun LoadingCenter() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = HFColors.OnSurface)
    }
}

@Composable
private fun EmptyPlanes(error: String?) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(HFColors.SurfaceElevated)
                .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(22.dp))
                .padding(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Flight,
                contentDescription = null,
                tint = HFColors.OnSurface,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No planes yet",
                color = HFColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                error ?: "An admin can add aircraft from the Mac app — they'll show up here automatically.",
                color = HFColors.OnSurfaceMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PlaneList(
    planes: List<HFPlane>,
    workLogCountByPlane: Map<String, Int>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Planes",
                    color = HFColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "${planes.size}",
                    color = HFColors.OnSurfaceMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        items(planes, key = { it.id }) { plane ->
            PlaneCard(
                plane = plane,
                workLogCount = workLogCountByPlane[plane.id] ?: 0
            )
        }
    }
}

@Composable
private fun PlaneCard(plane: HFPlane, workLogCount: Int) {
    val accent = runCatching {
        plane.outlineHex
            ?.removePrefix("#")
            ?.takeIf { it.length == 6 }
            ?.let { Color("FF$it".toLong(16)) }
    }.getOrNull() ?: HFColors.OnSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.SurfaceElevated)
            .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.20f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Flight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                plane.tailNumber,
                color = HFColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            val subtitle = plane.displayName.takeIf { it.isNotBlank() }
                ?: "Assigned to the shop"
            Text(
                subtitle,
                color = HFColors.OnSurfaceMuted,
                fontSize = 12.sp
            )
        }
        Text(
            text = "$workLogCount",
            color = HFColors.OnSurfaceFaint,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
