package com.hangarflow.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.BuildConfig
import com.hangarflow.app.R
import com.hangarflow.app.ui.theme.HFColors
import com.hangarflow.app.ui.theme.HFDarkPalette
import androidx.compose.ui.res.stringResource

/**
 * The boot screen, shown while the session is being restored.
 *
 * The launch-window theme already paints the lockup on black (see
 * `splash_background.xml`), so the window never flashes white — but the
 * instant Compose took over, that lockup was replaced by a bare spinner.
 * The handoff was the jarring part: the brand appeared, then vanished.
 * This continues the same image into the Compose frame so the two are
 * indistinguishable, then holds it until auth resolves.
 *
 * Same composition as the Apple `HFLaunchSplashView` and the desktop
 * `HFLaunchSplash`. The sweep is indeterminate deliberately: there is no
 * honest progress value to report through an auth round-trip.
 */
/**
 * Sized to match the logo the SYSTEM draws before we get a frame, so the
 * handoff into Compose doesn't resize it.
 *
 * 122dp rather than the 132dp the other platforms use, and that is measured,
 * not guessed. On API 31+ Android ignores a legacy `windowBackground` splash
 * drawable entirely and draws its own splash from the launcher icon at a
 * platform-fixed size — setting `android:width` in `splash_background.xml`
 * changes nothing there (verified: 180dp, 142dp and 132dp all rendered the
 * same 154px of ink on an API 35 device). So Compose matches the platform
 * instead of the reverse: 132dp rendered 166px of ink, 122dp renders 154px.
 *
 * Because the platform size is a dp constant, this holds across densities.
 * Re-measure if the launcher icon artwork changes.
 */
private val LOGO_SIZE = 122.dp

/*
 * The splash is deliberately NOT theme-aware, and reads HFDarkPalette directly.
 *
 * The launch window underneath it is drawn by the system from the app's theme
 * before a single line of our code runs, so it cannot know that this user chose
 * Light. It can only be made to follow the *device* (values-night), which is a
 * different question and still disagrees whenever the in-app override does.
 * Any theme-aware splash therefore means a black system window flashing to a
 * white Compose one, every cold start.
 *
 * Treating the half-second brand moment as a fixed asset — the way an iOS launch
 * storyboard is fixed — removes the flash outright. The app's real first screen
 * is the one after this, and that one honours the setting.
 */
@Composable
fun HFLaunchSplash() {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(HFDarkPalette.background)
    ) {
        // The logo is centred on its own, NOT as the first item of a centred
        // column. The launch-window drawable centres this same image at this
        // same size, so centring it the same way here is what makes the
        // handoff invisible. Centring the whole column instead pushed the logo
        // ~52dp above centre, and it jumped up the moment Compose took over.
        Image(
            painter = painterResource(R.drawable.hf_brand_logo),
            contentDescription = "Hangar Flow",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(LOGO_SIZE).align(Alignment.Center)
        )

        // Everything else hangs BELOW the centred logo, so adding or changing
        // any of it can never move the logo again.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight / 2 + LOGO_SIZE / 2 + 18.dp)
        ) {
            Text(
                "Hangar Flow",
                color = HFDarkPalette.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                stringResource(R.string.splash_tagline),
                color = HFDarkPalette.onSurface.copy(alpha = 0.42f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )

            SweepBar(modifier = Modifier.padding(top = 34.dp))
        }

        Text(
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            color = HFDarkPalette.onSurface.copy(alpha = 0.30f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
        )
    }
}

@Composable
private fun SweepBar(modifier: Modifier = Modifier, width: Dp = 168.dp) {
    val transition = rememberInfiniteTransition(label = "splash-sweep")
    val progress by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    BoxWithConstraints(
        modifier = modifier
            .width(width)
            .height(3.dp)
            .clip(CircleShape)
            .background(HFDarkPalette.onSurface.copy(alpha = 0.10f))
    ) {
        val thumb = maxWidth * 0.34f
        Box(
            modifier = Modifier
                .offset(x = maxWidth * progress)
                .width(thumb)
                .fillMaxSize()
                .clip(CircleShape)
                .background(HFDarkPalette.onSurface.copy(alpha = 0.85f))
        )
    }
}
