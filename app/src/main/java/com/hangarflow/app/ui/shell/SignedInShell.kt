package com.hangarflow.app.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hangarflow.app.ui.planes.PlanesTab
import com.hangarflow.app.ui.theme.HFColors
import com.hangarflow.app.ui.worklogs.WorkLogsTab

/**
 * Full post-auth shell. Matches the macOS `HFDesktopSignedInShell`
 * structure: header + tab bar + tab content. Tab selection survives
 * config changes (rotation) via `rememberSaveable`.
 */
@Composable
fun SignedInShell(
    orgName: String,
    role: String,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable(stateSaver = HFTabSaver) {
        mutableStateOf(HFTab.Planes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        HFTopBar(
            orgName = orgName,
            role = role,
            onSignOut = onSignOut
        )
        HorizontalDivider(color = HFColors.OutlineSubtle)
        HFTabBar(
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )
        HorizontalDivider(color = HFColors.OutlineSubtle)

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab-content"
            ) { tab ->
                when (tab) {
                    HFTab.Planes -> PlanesTab()
                    HFTab.WorkLogs -> WorkLogsTab()
                    HFTab.ControlCenter -> ControlCenterTab()
                    HFTab.Settings -> SettingsTab()
                }
            }
        }
    }
}

private val HFTabSaver = Saver<HFTab, String>(
    save = { it.name },
    restore = { runCatching { HFTab.valueOf(it) }.getOrDefault(HFTab.Planes) }
)
