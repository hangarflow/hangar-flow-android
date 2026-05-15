package com.hangarflow.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.ui.theme.HFColors

@Composable
fun SettingsTab() {
    val state by AuthManager.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Settings",
            color = HFColors.OnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        SettingsCard(title = "Shop") {
            SettingsRow(label = "Organization", value = state.orgName)
            SettingsRow(label = "Your role", value = state.role.replaceFirstChar { it.titlecase() })
        }

        Spacer(Modifier.height(16.dp))

        SettingsCard(title = "Sync") {
            SettingsRow(label = "Backend", value = "Connected")
            SettingsRow(label = "Realtime", value = "Pending (Phase 5)")
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { AuthManager.signOut() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HFColors.StatusRed.copy(alpha = 0.12f),
                contentColor = HFColors.StatusRed
            )
        ) {
            Text("Sign out", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.SurfaceElevated)
            .border(1.dp, HFColors.OutlineSubtle, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = HFColors.OnSurfaceMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 13.sp)
        Text(value, color = HFColors.OnSurfaceMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
