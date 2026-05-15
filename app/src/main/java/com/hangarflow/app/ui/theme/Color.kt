package com.hangarflow.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hangar Flow brand palette. The iOS + macOS apps live on a near-pure black
 * canvas with white text and high-contrast accent pills. Android mirrors
 * that so shops feel the same product across platforms.
 */
object HFColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val SurfaceElevated = Color(0xFF181818)
    val SurfaceHover = Color(0xFF242424)
    val OutlineSubtle = Color(0x1AFFFFFF)  // white @ 10%
    val OutlineStrong = Color(0x33FFFFFF)  // white @ 20%

    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceMuted = Color(0xFFB3B3B3)
    val OnSurfaceFaint = Color(0x66FFFFFF)

    // Status colors used across Work Logs / Squawks / Parts to Order
    val StatusGreen = Color(0xFF30D158)
    val StatusOrange = Color(0xFFFF9F0A)
    val StatusRed = Color(0xFFFF453A)
    val StatusYellow = Color(0xFFFFD60A)
    val StatusBlue = Color(0xFF0A84FF)
    val StatusCyan = Color(0xFF64D2FF)
    val StatusPurple = Color(0xFFBF5AF2)

    // Brand accents
    val BrandWhite = Color(0xFFFFFFFF)
    val BrandInk = Color(0xFF000000)
}
