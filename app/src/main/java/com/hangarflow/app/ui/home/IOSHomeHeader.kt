package com.hangarflow.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.hangarflow.app.ui.theme.HFColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Port of the iOS tech home header. Big bold title (tech's name for
 * techs, org name for admins), small date/time line above, sync badge
 * under it, three round pill-capped action buttons on the right.
 */
@Composable
fun IOSHomeHeader(
    primaryTitle: String,
    subtitle: String?,
    syncStatus: String,
    onGoHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    val now = remember { LocalDateTime.now() }
    val dateLine = remember(now) {
        val time = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        val day = now.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault()))
        "$time · $day"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = dateLine,
                color = HFColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = primaryTitle.ifBlank { "Hangar Flow" },
                color = HFColors.OnSurface,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.size(4.dp))
            IOSSyncBadge(statusText = syncStatus)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderIconButton(icon = Icons.Filled.Home, onClick = onGoHome)
            HeaderIconButton(icon = Icons.Outlined.Settings, onClick = onOpenSettings)
            HeaderIconButton(icon = Icons.Outlined.Logout, onClick = onSignOut)
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(HFColors.OnSurface.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HFColors.OnSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}
