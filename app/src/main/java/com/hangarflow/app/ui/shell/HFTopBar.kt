package com.hangarflow.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.ui.theme.HFColors

/**
 * Top-of-shell header. Mirrors the macOS `HFDesktopHeader` — brand
 * lockup at the left, org name + role pill in the middle, sign-out
 * button at the right.
 */
@Composable
fun HFTopBar(
    orgName: String,
    role: String,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand tile — matches the Mac "HF" monogram block
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HFColors.SurfaceElevated)
                .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "HF",
                color = HFColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = orgName.ifBlank { "Hangar Flow" },
                color = HFColors.OnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = role.replaceFirstChar { it.titlecase() },
                    color = HFColors.OnSurfaceMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                SyncStatusPill()
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onSignOut,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(HFColors.SurfaceElevated)
                .border(1.dp, HFColors.OutlineSubtle, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.Logout,
                contentDescription = "Sign out",
                tint = HFColors.OnSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SyncStatusPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.StatusGreen.copy(alpha = 0.12f))
            .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.25f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(HFColors.StatusGreen)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Synced",
            color = HFColors.StatusGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
