package com.hangarflow.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.runtime.Composable
import com.hangarflow.app.ui.common.HFTabPlaceholder

@Composable
fun ControlCenterTab() {
    HFTabPlaceholder(
        title = "Control Center",
        subtitle = "Squawks, parts queues, manuals, timecards — the shop's nerve center.",
        icon = Icons.Outlined.Dashboard,
        phaseNote = "Phase 6 — admin tools"
    )
}
