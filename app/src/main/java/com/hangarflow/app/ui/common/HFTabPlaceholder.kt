package com.hangarflow.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.ui.theme.HFColors

/**
 * Empty-state card reused by every not-yet-ported tab. Gives users a
 * visible anchor for each tab until Phase 5+ wires the real content.
 */
@Composable
fun HFTabPlaceholder(
    title: String,
    subtitle: String,
    icon: ImageVector,
    phaseNote: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(HFColors.SurfaceElevated)
                .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(22.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HFColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = HFColors.OnSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                color = HFColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = HFColors.OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = phaseNote.uppercase(),
                color = HFColors.OnSurfaceFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}
