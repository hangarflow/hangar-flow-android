package com.hangarflow.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.ui.graphics.vector.ImageVector
import com.hangarflow.app.R
import androidx.annotation.StringRes

/**
 * Mirror of the macOS top-tab set: Planes / Work Logs / Control Center /
 * Settings. Order matches the Mac UI so admins switching platforms see
 * the same mental map.
 */
enum class HFTab(@StringRes val title: Int, val icon: ImageVector) {
    Planes(R.string.nav_planes, Icons.Rounded.Flight),
    WorkLogs(R.string.nav_work_logs, Icons.Outlined.ListAlt),
    ControlCenter(R.string.tab_control_center, Icons.Outlined.Dashboard),
    Settings(R.string.nav_settings, Icons.Outlined.Settings)
}
