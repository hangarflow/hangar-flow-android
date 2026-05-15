package com.hangarflow.app.ui.worklogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hangarflow.app.data.model.HFStatusFilter
import com.hangarflow.app.data.model.HFWorkCategory
import com.hangarflow.app.data.model.HFWorkLog
import com.hangarflow.app.data.model.HFWorkLogStatus
import com.hangarflow.app.ui.theme.HFColors

@Composable
fun WorkLogsTab() {
    val state by SharedStore.state.collectAsState()
    var statusFilter by remember { mutableStateOf(HFStatusFilter.All) }
    var categoryFilter by remember { mutableStateOf<HFWorkCategory?>(null) }

    val filtered = remember(state.workLogs, statusFilter, categoryFilter) {
        state.workLogs.filter { log ->
            val status = HFWorkLogStatus.fromRaw(log.status)
            val category = HFWorkCategory.fromRaw(log.category)
            statusFilter.matches(status) &&
                (categoryFilter == null || categoryFilter == category)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        WorkLogsHeader(total = filtered.size, overall = state.workLogs.size)
        Spacer(Modifier.height(12.dp))
        StatusFilterRow(selected = statusFilter, onSelect = { statusFilter = it })
        Spacer(Modifier.height(10.dp))
        CategoryFilterRow(
            selected = categoryFilter,
            onSelect = { categoryFilter = it }
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            EmptyWorkLogs(
                message = if (state.workLogs.isEmpty())
                    "No work logs yet — admins import them from the Mac."
                else
                    "No work logs match the current filters."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { log ->
                    WorkLogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun WorkLogsHeader(total: Int, overall: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Work Logs",
            color = HFColors.OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(10.dp))
        Text(
            if (total == overall) "$total" else "$total / $overall",
            color = HFColors.OnSurfaceMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: HFStatusFilter,
    onSelect: (HFStatusFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HFStatusFilter.entries.forEach { filter ->
            FilterPill(
                label = filter.label,
                isSelected = filter == selected,
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: HFWorkCategory?,
    onSelect: (HFWorkCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterPill(
            label = "All",
            isSelected = selected == null,
            onClick = { onSelect(null) }
        )
        HFWorkCategory.entries.forEach { cat ->
            FilterPill(
                label = cat.label,
                isSelected = selected == cat,
                onClick = { onSelect(cat) }
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) HFColors.BrandWhite else HFColors.SurfaceElevated
    val fg = if (isSelected) HFColors.BrandInk else HFColors.OnSurface
    val border = if (isSelected) HFColors.BrandWhite else HFColors.OutlineSubtle

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WorkLogCard(log: HFWorkLog) {
    val status = HFWorkLogStatus.fromRaw(log.status)
    val category = HFWorkCategory.fromRaw(log.category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.SurfaceElevated)
            .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.title.ifBlank { "Untitled work log" },
                    color = HFColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaPill(
                        text = log.planeTailNumber.ifBlank { "No tail" },
                        color = HFColors.OnSurfaceMuted
                    )
                    Spacer(Modifier.size(6.dp))
                    MetaPill(text = category.label, color = HFColors.OnSurfaceMuted)
                    if (!log.assignedUserName.isNullOrBlank()) {
                        Spacer(Modifier.size(6.dp))
                        MetaPill(text = log.assignedUserName, color = HFColors.StatusCyan)
                    }
                }
            }
            StatusDot(color = status.color, label = status.label)
        }

        if (log.details.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = log.details,
                color = HFColors.OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 3
            )
        }

        if (log.loggedMinutes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMinutes(log.loggedMinutes),
                color = HFColors.OnSurfaceFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
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
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyWorkLogs(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = HFColors.OnSurfaceMuted,
            fontSize = 13.sp
        )
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m logged"
        m == 0 -> "${h}h logged"
        else -> "${h}h ${m}m logged"
    }
}
