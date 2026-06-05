package com.hangarflow.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.ui.theme.HFColors

/**
 * Floating "+" button visible only to admins. Wraps the hub content and
 * opens the matching create sheet.
 */
@Composable
fun AdminFabOverlay(
    mode: AdminCreateMode,
    content: @Composable () -> Unit
) {
    val auth by AuthManager.state.collectAsState()
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        // Lead techs can create planes / work logs / invite teammates too
        // (the invite sheet blocks them from creating admins). No file
        // import lives here, so this stays within the laptop-only rule.
        if (auth.isAdmin || auth.isLeadTech) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface)
                    .clickable { open = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Create",
                    tint = HFColors.BrandInk,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (open) {
            AdminCreateSheet(mode = mode, onDismiss = { open = false })
        }
    }
}

