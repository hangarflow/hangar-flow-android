package com.hangarflow.app.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.hangarflow.app.ui.common.hfPressClickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.ui.hubs.EquipmentHub
import com.hangarflow.app.ui.hubs.QuickPicScreen
import com.hangarflow.app.ui.theme.HFColors

/** Control Center tools available on mobile. */
private enum class CCTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
) {
    Equipment("Equipment", "Shop gear — maintenance & calibration due", Icons.Outlined.Build, HFColors.StatusGreen),
    QuickPic("QuickPic", "Scan or print QR labels for parts & equipment", Icons.Outlined.QrCodeScanner, HFColors.StatusBlue)
}

@Composable
fun ControlCenterTab() {
    var active by remember { mutableStateOf<CCTool?>(null) }

    // iOS-style push/pop: opening a tool slides in from the right, going back
    // slides it back out — so the Control Center feels like a nav stack.
    AnimatedContent(
        targetState = active,
        transitionSpec = {
            val dur = 260
            if (targetState != null) {
                (slideInHorizontally(tween(dur)) { it } + fadeIn(tween(dur))) togetherWith
                    (slideOutHorizontally(tween(dur)) { -it / 5 } + fadeOut(tween(dur)))
            } else {
                (slideInHorizontally(tween(dur)) { -it / 5 } + fadeIn(tween(dur))) togetherWith
                    (slideOutHorizontally(tween(dur)) { it } + fadeOut(tween(dur)))
            }
        },
        label = "cc-tool"
    ) { tool ->
        when (tool) {
            null -> CCToolGrid(onOpen = { active = it })
            CCTool.Equipment -> ToolFrame("Equipment", onBack = { active = null }) { EquipmentHub() }
            CCTool.QuickPic -> ToolFrame("QuickPic", onBack = { active = null }) { QuickPicScreen() }
        }
    }
}

@Composable
private fun CCToolGrid(onOpen: (CCTool) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Control Center",
            color = HFColors.OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Shop tools every tech can reach.",
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.size(4.dp))
        CCTool.entries.forEach { tool ->
            ToolCard(tool, onClick = { onOpen(tool) })
        }
    }
}

@Composable
private fun ToolCard(tool: CCTool, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hfPressClickable(onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, tool.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tool.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, null, tint = tool.accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tool.title, color = HFColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(tool.subtitle, color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ToolFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = HFColors.OnSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(6.dp))
            Text(title, color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
