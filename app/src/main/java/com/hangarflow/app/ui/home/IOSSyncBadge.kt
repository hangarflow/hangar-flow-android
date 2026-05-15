package com.hangarflow.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.ui.theme.HFColors

/**
 * Port of iOS `HFMobileSyncBadge`. Small pill that sits under the
 * "Hangar Flow" title showing Synced / Syncing / Offline state.
 */
@Composable
fun IOSSyncBadge(statusText: String) {
    val preset = remember(statusText)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(preset.color.copy(alpha = 0.14f))
            .border(1.dp, preset.color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = preset.icon,
            contentDescription = null,
            tint = preset.color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            preset.label,
            color = preset.color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class SyncPreset(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private fun remember(statusText: String): SyncPreset {
    val lower = statusText.lowercase()
    return when {
        "checking" in lower ->
            SyncPreset("Syncing", Icons.Outlined.CloudSync, HFColors.StatusBlue)
        "active" in lower || "ready" in lower || "synced" in lower ->
            SyncPreset("Synced", Icons.Outlined.CloudDone, HFColors.StatusGreen)
        "local" in lower ->
            SyncPreset("Local", Icons.Outlined.Cloud, HFColors.StatusOrange)
        "error" in lower || "fail" in lower ->
            SyncPreset("Offline", Icons.Outlined.CloudOff, HFColors.StatusRed)
        else -> SyncPreset("Synced", Icons.Outlined.CloudDone, HFColors.StatusGreen)
    }
}
