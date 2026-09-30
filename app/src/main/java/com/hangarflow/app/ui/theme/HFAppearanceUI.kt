package com.hangarflow.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.R

private fun icon(mode: HFAppearance.Mode): ImageVector = when (mode) {
    HFAppearance.Mode.SYSTEM -> Icons.Outlined.Brightness4
    HFAppearance.Mode.LIGHT -> Icons.Outlined.LightMode
    HFAppearance.Mode.DARK -> Icons.Outlined.DarkMode
}

@Composable
private fun label(mode: HFAppearance.Mode): String = stringResource(
    when (mode) {
        HFAppearance.Mode.SYSTEM -> R.string.appearance_system
        HFAppearance.Mode.LIGHT -> R.string.appearance_light
        HFAppearance.Mode.DARK -> R.string.appearance_dark
    }
)

/**
 * The Settings control: three segments rather than an on/off switch.
 *
 * "System" is a real answer — a shop tablet that dims itself at night should
 * be allowed to follow the device — so a two-state toggle could not express
 * what people actually want.
 */
@Composable
fun HFAppearanceSettingsRow() {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Text(
            stringResource(R.string.settings_appearance),
            color = HFColors.OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HFAppearance.Mode.entries.forEach { mode ->
                val selected = HFAppearance.mode == mode
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (selected) HFColors.BrandWhite else HFColors.OutlineSubtle,
                            RoundedCornerShape(100.dp)
                        )
                        .clickable { HFAppearance.save(context, mode) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon(mode),
                        contentDescription = null,
                        tint = if (selected) HFColors.BrandInk else HFColors.OnSurface.copy(alpha = 0.72f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label(mode),
                        color = if (selected) HFColors.BrandInk else HFColors.OnSurface.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Text(
            stringResource(R.string.settings_appearance_desc),
            color = HFColors.OnSurface.copy(alpha = 0.45f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * The sign-in pill, next to the language one. Same reason: a tech standing on
 * a bright ramp should be able to make the screen readable before they have
 * typed a password, not after.
 *
 * It names the mode you will GET, not the one you are in — in dark it says
 * "Light", in light it says "Dark". A control that reports the current state
 * reads as a label; one that names the result reads as a button, and this is a
 * button. It flips against the RESOLVED scheme, so it still says the right
 * thing when the mode is System.
 *
 * Two-way on purpose. System is a real preference but not one anyone wants to
 * land on mid-cycle, so it lives in Settings where it can be chosen
 * deliberately.
 */
@Composable
fun HFAppearanceButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val goingToDark = !HFColors.isDark
    val target = if (goingToDark) HFAppearance.Mode.DARK else HFAppearance.Mode.LIGHT

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .clickable { HFAppearance.save(context, target) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon(target),
            contentDescription = label(target),
            tint = HFColors.OnSurface.copy(alpha = 0.70f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label(target),
            color = HFColors.OnSurface.copy(alpha = 0.80f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
