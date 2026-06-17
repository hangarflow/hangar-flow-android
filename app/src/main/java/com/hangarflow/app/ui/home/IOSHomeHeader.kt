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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.hangarflow.app.ui.theme.HFColors

/**
 * Port of the iOS tech home header. Big bold title (tech's name for
 * techs, org name for admins), sync badge under it, three round
 * pill-capped action buttons on the right. The system status bar
 * already shows the current time, so we don't render an in-app
 * clock line to avoid duplicating it.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Org line: building icon + org name, small/bold/kerned —
            // mirrors the iOS `planesDashboardHeader` top row so it's
            // always clear which shop's data is on screen.
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apartment,
                        contentDescription = null,
                        tint = HFColors.OnSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        it,
                        color = HFColors.OnSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = primaryTitle.ifBlank { "Hangar Flow" },
                color = HFColors.OnSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            IOSSyncBadge(statusText = syncStatus)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderIconButton(icon = Icons.Filled.Home, onClick = onGoHome)
            HeaderIconButton(icon = Icons.Filled.Settings, onClick = onOpenSettings)
            HeaderIconButton(icon = Icons.AutoMirrored.Filled.Logout, onClick = onSignOut)
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
