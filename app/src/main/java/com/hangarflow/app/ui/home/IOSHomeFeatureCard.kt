package com.hangarflow.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
 * Port of iOS `IOSHomeFeatureCard`. A tappable tile with a colored icon
 * square in the top-left, chevron in the top-right, bold title, and an
 * optional subtitle. Padding, sizing, corner radius, opacity values match
 * the iOS file line-for-line so both platforms read identical visually.
 */
@Composable
fun IOSHomeFeatureCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    showSubtitle: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Tablet-density iPad match: taller card, bigger icon tile (56dp),
    // more internal padding and breathing room between icon/title/sub.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (showSubtitle && !subtitle.isNullOrBlank()) 168.dp else 132.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.5.dp, accent, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = HFColors.OnSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            title,
            color = HFColors.OnSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        if (showSubtitle && !subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = HFColors.OnSurface.copy(alpha = 0.68f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
    }
}
