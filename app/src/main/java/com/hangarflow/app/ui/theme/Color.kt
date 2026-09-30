package com.hangarflow.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Hangar Flow brand palette, in both schemes.
 *
 * The app was dark-only: a near-pure black canvas with white text and
 * high-contrast accent pills, matching iPhone and Mac. Light mode adds the
 * other side without moving the dark one — every dark value below is exactly
 * what shipped before this file grew a second palette.
 *
 * ## Why the tokens are `get()` over a snapshot state
 *
 * Call sites read `HFColors.Background` roughly 1,800 times, almost all of
 * them inside composables. Making them `val`s of a chosen palette would mean
 * rewriting every one of those, or restarting the process to change theme.
 * A `mutableStateOf` read inside composition is tracked by Compose, so
 * swapping [palette] recomposes everything that touched a colour — and not one
 * call site had to change.
 *
 * `HangarFlowTheme` owns the swap; nothing else should write [palette].
 */
data class HFPalette(
    val isDark: Boolean,

    val background: Color,
    /** The mid stop of the sign-in page's diagonal gradient. */
    val backgroundAlt: Color,
    /** The cool depth wash behind the sign-in panel; always used with an alpha. */
    val glow: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHover: Color,
    val outlineSubtle: Color,
    val outlineStrong: Color,

    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,

    val statusGreen: Color,
    val statusOrange: Color,
    val statusRed: Color,
    val statusYellow: Color,
    val statusBlue: Color,
    val statusCyan: Color,
    val statusPurple: Color,

    /** A solid accent surface — the primary button, a selected chip. */
    val brandSolid: Color,
    /** Text and icons ON [brandSolid]; inverts with it. */
    val brandOnSolid: Color,
    /** A dark wash over imagery. Stays dark in both schemes. */
    val scrim: Color,
)

/** Exactly what the app shipped before light mode existed. */
val HFDarkPalette = HFPalette(
    isDark = true,

    background = Color(0xFF000000),
    backgroundAlt = Color(0xFF0D0F14),
    glow = Color(0xFFDBE6F2),
    surface = Color(0xFF0E0E0E),
    surfaceElevated = Color(0xFF181818),
    surfaceHover = Color(0xFF242424),
    outlineSubtle = Color(0x1AFFFFFF),   // white @ 10%
    outlineStrong = Color(0x33FFFFFF),   // white @ 20%

    onSurface = Color(0xFFFFFFFF),
    onSurfaceMuted = Color(0xFFB3B3B3),
    onSurfaceFaint = Color(0x66FFFFFF),

    statusGreen = Color(0xFF30D158),
    statusOrange = Color(0xFFFF9F0A),
    statusRed = Color(0xFFFF453A),
    statusYellow = Color(0xFFFFD60A),
    statusBlue = Color(0xFF0A84FF),
    statusCyan = Color(0xFF64D2FF),
    statusPurple = Color(0xFFBF5AF2),

    brandSolid = Color(0xFFFFFFFF),
    brandOnSolid = Color(0xFF000000),
    scrim = Color(0xCC000000),
)

/**
 * Light mode. Not an inversion of the dark values — a white page with grey
 * surfaces needs its own ladder.
 *
 * The status hues are darker and more saturated than the dark ones on purpose.
 * System green on white is about 2.2:1 against the page, which fails AA for
 * the 11–13sp labels this app puts it on; these clear 4.5:1. Same values the
 * iOS and macOS clients use, so a shop running both sees one product.
 */
val HFLightPalette = HFPalette(
    isDark = false,

    background = Color(0xFFFFFFFF),
    backgroundAlt = Color(0xFFF4F6F8),
    // Deeper than the dark glow: a pale wash that reads as depth on black is
    // invisible on white, so light mode needs a colour with somewhere to fall.
    glow = Color(0xFF8FB3DC),
    surface = Color(0xFFF4F6F8),
    surfaceElevated = Color(0xFFEDF0F4),
    surfaceHover = Color(0xFFE4E8EE),
    outlineSubtle = Color(0x1F0D1117),   // near-black @ 12%
    outlineStrong = Color(0x3D0D1117),   // near-black @ 24%

    // Near-black rather than pure black: easier on the eye under hangar
    // lighting and still far clear of AA.
    onSurface = Color(0xFF0D1117),
    onSurfaceMuted = Color(0xFF5A626D),
    onSurfaceFaint = Color(0x8C0D1117),

    statusGreen = Color(0xFF18753C),
    statusOrange = Color(0xFFB45309),
    statusRed = Color(0xFFC62828),
    statusYellow = Color(0xFF8A6100),
    statusBlue = Color(0xFF1D4ED8),
    statusCyan = Color(0xFF0E7490),
    statusPurple = Color(0xFF7E22CE),

    brandSolid = Color(0xFF111827),
    brandOnSolid = Color(0xFFFFFFFF),
    scrim = Color(0xCC000000),           // over photos; not theme-aware
)

/**
 * The palette every screen reads. Token names are unchanged from the
 * dark-only version, so the whole app recolours from here.
 */
object HFColors {

    /** Swapped by [HangarFlowTheme]. Reading it in composition tracks it. */
    var palette by mutableStateOf(HFDarkPalette)
        internal set

    val isDark: Boolean get() = palette.isDark

    val Background: Color get() = palette.background
    val BackgroundAlt: Color get() = palette.backgroundAlt
    val Glow: Color get() = palette.glow
    val Surface: Color get() = palette.surface
    val SurfaceElevated: Color get() = palette.surfaceElevated
    val SurfaceHover: Color get() = palette.surfaceHover
    val OutlineSubtle: Color get() = palette.outlineSubtle
    val OutlineStrong: Color get() = palette.outlineStrong

    val OnSurface: Color get() = palette.onSurface
    val OnSurfaceMuted: Color get() = palette.onSurfaceMuted
    val OnSurfaceFaint: Color get() = palette.onSurfaceFaint

    val StatusGreen: Color get() = palette.statusGreen
    val StatusOrange: Color get() = palette.statusOrange
    val StatusRed: Color get() = palette.statusRed
    val StatusYellow: Color get() = palette.statusYellow
    val StatusBlue: Color get() = palette.statusBlue
    val StatusCyan: Color get() = palette.statusCyan
    val StatusPurple: Color get() = palette.statusPurple

    /**
     * A solid accent surface. This was literally `Color.White` — in light mode
     * it becomes near-black, so the primary button still reads as the
     * strongest thing on screen.
     */
    val BrandWhite: Color get() = palette.brandSolid

    /** Text on [BrandWhite]. Was literally `Color.Black`; inverts with it. */
    val BrandInk: Color get() = palette.brandOnSolid

    /** A dark wash over a photo. Deliberately NOT theme-aware. */
    val Scrim: Color get() = palette.scrim

    /**
     * Text and icons sitting ON a saturated status colour — the red Clock Out
     * button, the blue Submit, a tinted action tile.
     *
     * White in BOTH schemes on purpose. The status hues are bright in dark and
     * deep in light, and white clears 4.5:1 on either; swapping this to
     * near-black in light would put dark text on a dark red button.
     */
    val OnAccent: Color get() = Color.White

    // ---------------------------------------------------------------------
    // Alpha ladders
    //
    // The same alpha is NOT the same thing in both schemes, because the base
    // colour flips. White at 55% over black lands around 6.3:1; near-black at
    // 55% over white lands around 4.2:1 — the light copy of a screen that was
    // designed in dark comes out washed out and slightly illegible, even though
    // the numbers match.
    //
    // So each role gets its own curve, and call sites keep writing the alpha
    // they designed in dark:
    //
    //   ink(o)    text and icons     — o + (1-o)*0.22, headroom toward opaque
    //   fill(o)   surfaces, washes   — o * 1.35, capped
    //   stroke(o) hairlines, borders — o * 2.1, capped
    //
    // Strokes need the steepest curve: a 0.06 white hairline is a visible edge
    // on black, and the same near-black hairline on white is nothing at all.
    // They cannot use the ink curve — headroom would turn that 0.06 hairline
    // into a 0.27 line and box every card in grey.
    //
    // Identical to the iOS/macOS `hfInk`/`hfFill`/`hfStroke` tokens, so the two
    // clients render the same screen the same way.
    // ---------------------------------------------------------------------

    /** Text and icons at partial strength. */
    fun ink(o: Float): Color = palette.onSurface.copy(
        alpha = if (palette.isDark) o else (o + (1f - o) * 0.22f).coerceAtMost(1f)
    )

    /** A surface, tile or wash drawn in the ink colour. */
    fun fill(o: Float): Color = palette.onSurface.copy(
        alpha = if (palette.isDark) o else (o * 1.35f).coerceAtMost(0.9f)
    )

    /** A border, hairline or divider. */
    fun stroke(o: Float): Color = palette.onSurface.copy(
        alpha = if (palette.isDark) o else (o * 2.1f).coerceAtMost(0.9f)
    )
}
