package com.hangarflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Hangar Flow's theme. No dynamic Material You colours — every shop device
 * should look identical to the iOS/macOS clients.
 *
 * The scheme comes from [HFAppearance], which the user sets in Settings.
 * Swapping [HFColors.palette] is what actually recolours the app: the ~1,800
 * `HFColors.X` reads are snapshot-state reads, so they recompose in place.
 * The Material scheme below matters much less — it only reaches the handful of
 * stock components the app uses — but it has to agree, or a Material surface
 * would sit black on a white page.
 */
@Composable
fun HangarFlowTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    HFAppearance.load(context)

    val systemDark = isSystemInDarkTheme()
    val dark = when (HFAppearance.mode) {
        HFAppearance.Mode.SYSTEM -> systemDark
        HFAppearance.Mode.LIGHT -> false
        HFAppearance.Mode.DARK -> true
    }

    val palette = if (dark) HFDarkPalette else HFLightPalette
    // Assign rather than remember: HFColors is a process-wide facade, and the
    // non-composable readers (notification builders, widgets) need the current
    // value too.
    HFColors.palette = palette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Status-bar icons have to flip with the page or they vanish:
            // light icons on a white bar are invisible.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightNavigationBars = !dark
        }
    }

    val scheme = remember(palette) {
        if (dark) {
            darkColorScheme(
                primary = palette.brandSolid,
                onPrimary = palette.brandOnSolid,
                secondary = palette.statusBlue,
                onSecondary = palette.onSurface,
                tertiary = palette.statusCyan,
                background = palette.background,
                onBackground = palette.onSurface,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceElevated,
                onSurfaceVariant = palette.onSurfaceMuted,
                outline = palette.outlineStrong,
                outlineVariant = palette.outlineSubtle,
                error = palette.statusRed,
                onError = palette.brandOnSolid
            )
        } else {
            lightColorScheme(
                primary = palette.brandSolid,
                onPrimary = palette.brandOnSolid,
                secondary = palette.statusBlue,
                onSecondary = palette.brandOnSolid,
                tertiary = palette.statusCyan,
                background = palette.background,
                onBackground = palette.onSurface,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceElevated,
                onSurfaceVariant = palette.onSurfaceMuted,
                outline = palette.outlineStrong,
                outlineVariant = palette.outlineSubtle,
                error = palette.statusRed,
                onError = palette.brandOnSolid
            )
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
