package com.hangarflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Hangar Flow always runs in its brand dark theme — matches iPhone and Mac.
 * No dynamic Material You colors; we want every shop device to look
 * identical to the iOS/macOS clients.
 */
private val HFColorScheme = darkColorScheme(
    primary = HFColors.BrandWhite,
    onPrimary = HFColors.BrandInk,
    secondary = HFColors.StatusBlue,
    onSecondary = HFColors.OnSurface,
    tertiary = HFColors.StatusCyan,
    background = HFColors.Background,
    onBackground = HFColors.OnSurface,
    surface = HFColors.Surface,
    onSurface = HFColors.OnSurface,
    surfaceVariant = HFColors.SurfaceElevated,
    onSurfaceVariant = HFColors.OnSurfaceMuted,
    outline = HFColors.OutlineStrong,
    outlineVariant = HFColors.OutlineSubtle,
    error = HFColors.StatusRed,
    onError = HFColors.OnSurface
)

@Composable
fun HangarFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HFColorScheme,
        typography = Typography,
        content = content
    )
}
