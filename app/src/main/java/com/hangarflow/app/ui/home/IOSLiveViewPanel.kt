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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.ui.theme.HFColors

/**
 * Kotlin port of iOS `IOSLiveViewPanel`. Header row with the user's name,
 * small friendly sync label, Clock In / Clock Out button, and a 2×2 grid
 * of stat tiles (Today / Open Work / Manuals / Squawks).
 */
/** Phase of the 4-stage clock cycle shown by the live view button. */
enum class ClockPhase { Idle, Working, OnLunch, ReadyToClockOut }

@Composable
fun IOSLiveViewPanel(
    userName: String?,
    syncLabel: String,
    clockPhase: ClockPhase,
    onClockAction: () -> Unit,
    onSkipLunch: () -> Unit,
    todayHoursLabel: String,
    openAssignedCount: Int,
    manualsCount: Int,
    openSquawkCount: Int,
    isOffline: Boolean,
    onOpenTimeCard: (() -> Unit)? = null,
    onOpenWorkLogs: (() -> Unit)? = null,
    onOpenManuals: (() -> Unit)? = null,
    onOpenSquawks: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "Live View",
                color = HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = userName ?: "All techs",
                color = HFColors.OnSurface.copy(alpha = if (userName == null) 0.45f else 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = syncLabel,
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(10.dp))

        // Clock in / lunch out / lunch in / clock out cycle.
        ClockCycleButton(phase = clockPhase, onAction = onClockAction)

        // Secondary action — only shown while the tech is working and
        // hasn't taken lunch yet. Marks lunch as skipped so the lunch
        // prompt clears; does NOT clock out. Tech keeps working straight
        // through and clocks out normally at end of day.
        if (clockPhase == ClockPhase.Working) {
            Spacer(Modifier.height(6.dp))
            SkipLunchButton(onClick = onSkipLunch)
        }

        Spacer(Modifier.height(10.dp))

        // 2x2 stat grid — hand-laid to match iOS LazyVGrid spacing
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IOSStatTile(
                accent = HFColors.StatusGreen,
                label = "TODAY",
                value = todayHoursLabel,
                footer = if (userName == null) "shop hours" else "your hours",
                modifier = Modifier.weight(1f),
                onTap = onOpenTimeCard
            )
            IOSStatTile(
                accent = HFColors.StatusOrange,
                label = "OPEN WORK",
                value = "$openAssignedCount",
                footer = if (userName == null) "across shop" else "assigned to you",
                modifier = Modifier.weight(1f),
                onTap = onOpenWorkLogs
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IOSStatTile(
                accent = HFColors.StatusPurple,
                label = "MANUALS",
                value = "$manualsCount",
                footer = "available offline",
                modifier = Modifier.weight(1f),
                onTap = onOpenManuals
            )
            IOSStatTile(
                accent = HFColors.StatusRed,
                label = "SQUAWKS",
                value = "$openSquawkCount",
                footer = "still open",
                modifier = Modifier.weight(1f),
                onTap = onOpenSquawks
            )
        }

        if (isOffline) {
            Spacer(Modifier.height(10.dp))
            OfflineBanner()
        }
    }
}

@Composable
private fun ClockCycleButton(phase: ClockPhase, onAction: () -> Unit) {
    // Label + accent color + icon depend on what the user should do next.
    val (label, accent, icon) = when (phase) {
        ClockPhase.Idle -> Triple("Clock In", HFColors.StatusGreen, Icons.Filled.PlayCircle)
        ClockPhase.Working -> Triple("Lunch Out", HFColors.StatusOrange, Icons.Outlined.Restaurant)
        ClockPhase.OnLunch -> Triple("Lunch In", HFColors.StatusCyan, Icons.Outlined.WorkOutline)
        ClockPhase.ReadyToClockOut -> Triple("Clock Out", HFColors.StatusRed, Icons.Filled.StopCircle)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onAction)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SkipLunchButton(onClick: () -> Unit) {
    val accent = HFColors.StatusOrange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Skip Lunch",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IOSStatTile(
    accent: Color,
    label: String,
    value: String,
    footer: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .let { if (onTap != null) it.clickable(onClick = onTap) else it }
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = HFColors.OnSurface.copy(alpha = 0.60f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            color = HFColors.OnSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            footer,
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HFColors.StatusYellow.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = HFColors.StatusYellow.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Working offline — changes will sync when connected",
            color = HFColors.StatusYellow.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
