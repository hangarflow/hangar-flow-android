package com.hangarflow.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.ui.theme.HFColors

/**
 * Port of iOS `HFMobileSyncBadge`. Small pill that sits under the
 * "Hangar Flow" title showing Synced / Syncing / Offline state.
 *
 * While syncing, the circular-arrows glyph spins clockwise (matching iOS +
 * Compose Desktop) so the badge visibly "turns" as it pulls the newest
 * server data, and parks at rest otherwise. The Animatable only runs while
 * syncing, so there's no per-frame work the rest of the time.
 */
@Composable
fun IOSSyncBadge(statusText: String) {
    val preset = syncPreset(statusText)
    val isSyncing = preset.label == "Syncing"
    val spin = remember { Animatable(0f) }
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            spin.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            spin.snapTo(0f)
        }
    }
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
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = spin.value }
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

private fun syncPreset(statusText: String): SyncPreset {
    val lower = statusText.lowercase()
    return when {
        "checking" in lower ->
            SyncPreset("Syncing", Icons.Outlined.Sync, HFColors.StatusBlue)
        "active" in lower || "ready" in lower || "synced" in lower ->
            SyncPreset("Synced", Icons.Outlined.CloudDone, HFColors.StatusGreen)
        "local" in lower ->
            SyncPreset("Local", Icons.Outlined.Cloud, HFColors.StatusOrange)
        "error" in lower || "fail" in lower ->
            SyncPreset("Offline", Icons.Outlined.CloudOff, HFColors.StatusRed)
        else -> SyncPreset("Synced", Icons.Outlined.CloudDone, HFColors.StatusGreen)
    }
}
