package com.hangarflow.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
 * Horizontal row of tab pills. Selected tab gets a white fill / black
 * text treatment that matches the macOS admin UI exactly.
 */
@Composable
fun HFTabBar(
    selected: HFTab,
    onSelect: (HFTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HFTab.entries.forEach { tab ->
            HFTabPill(
                tab = tab,
                isSelected = tab == selected,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun HFTabPill(
    tab: HFTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val background = if (isSelected) HFColors.BrandWhite else HFColors.SurfaceElevated
    val contentColor = if (isSelected) HFColors.BrandInk else HFColors.OnSurface
    val borderColor = if (isSelected) HFColors.BrandWhite else HFColors.OutlineSubtle

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = tab.title,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
