package com.hangarflow.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Mirror of the macOS top-tab set: Planes / Work Logs / Control Center /
 * Settings. Order matches the Mac UI so admins switching platforms see
 * the same mental map.
 */
enum class HFTab(val title: String, val icon: ImageVector) {
    Planes("Planes", Icons.Rounded.Flight),
    WorkLogs("Work Logs", Icons.Outlined.ListAlt),
    ControlCenter("Control Center", Icons.Outlined.Dashboard),
    Settings("Settings", Icons.Outlined.Settings)
}
