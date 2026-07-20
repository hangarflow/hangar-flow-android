package com.hangarflow.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * iOS-style tap feedback: a subtle scale-down while the target is held,
 * with no Material ripple — mirrors the Apple app's `HFPressableButtonStyle`
 * so buttons and cards feel identical across platforms.
 */
fun Modifier.hfPressClickable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "hfPress")
    this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
