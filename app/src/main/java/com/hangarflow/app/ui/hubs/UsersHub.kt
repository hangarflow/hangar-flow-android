package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFUserProfile
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * Admin-only user roster. Lists every user in the org with role badge,
 * tap a user for actions (change role, delete). Tech users see an empty
 * placeholder — the home card is also gated on isAdmin so techs shouldn't
 * normally land here.
 */
@Composable
fun UsersHub() {
    HFPullToRefreshHost {
        UsersHubContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersHubContent() {
    val state by SharedStore.state.collectAsState()
    val auth by AuthManager.state.collectAsState()
    val isAdmin = auth.isAdmin
    // Lead techs manage members too (add/delete non-admins). Role changes
    // and acting on admin rows stay admin-only — enforced per-action below.
    val canManage = auth.canManageMembers

    val users = remember(state.users) {
        state.users.sortedWith(
            compareByDescending<HFUserProfile> { it.role.equals("admin", ignoreCase = true) }
                .thenBy { it.displayName.lowercase() }
        )
    }
    val admins = users.filter { it.role.equals("admin", ignoreCase = true) }
    val techs = users.filter { !it.role.equals("admin", ignoreCase = true) }

    var actionsFor by remember { mutableStateOf<HFUserProfile?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (!canManage) {
            IOSPlaceholderPanel(
                message = "User management is admin-only. Ask an admin to invite or manage teammates."
            )
            return@Column
        }

        Text(
            text = "${users.size} total · ${admins.size} admin · ${techs.size} tech",
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(12.dp))

        if (users.isEmpty()) {
            IOSPlaceholderPanel(message = "No users in this org yet.")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (admins.isNotEmpty()) {
                item { SectionLabel("ADMINS") }
                items(admins, key = { it.id }) { user ->
                    UserRow(user, onTap = { actionsFor = user })
                }
            }
            if (techs.isNotEmpty()) {
                item { Spacer(Modifier.size(6.dp)); SectionLabel("TECHS") }
                items(techs, key = { it.id }) { user ->
                    UserRow(user, onTap = { actionsFor = user })
                }
            }
        }
    }

    actionsFor?.let { user ->
        ModalBottomSheet(
            onDismissRequest = { actionsFor = null },
            sheetState = sheetState,
            containerColor = HFColors.Surface,
            contentColor = HFColors.OnSurface
        ) {
            UserActionsSheet(
                user = user,
                isSelf = state.currentUser?.id == user.id,
                onClose = { actionsFor = null }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = HFColors.OnSurface.copy(alpha = 0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun UserRow(user: HFUserProfile, onTap: () -> Unit) {
    val isAdmin = user.role.equals("admin", ignoreCase = true)
    val accent = if (isAdmin) HFColors.StatusBlue else HFColors.OnSurface.copy(alpha = 0.65f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            val initials = user.initials.ifBlank {
                user.displayName.trim().split(Regex("\\s+"))
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString("").take(2)
            }.ifBlank { "•" }
            Text(initials, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.displayName.ifBlank { "—" },
                color = HFColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                user.email.ifBlank { "No email" },
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(accent.copy(alpha = 0.20f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                user.role.replaceFirstChar { it.uppercase() },
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
private fun UserActionsSheet(
    user: HFUserProfile,
    isSelf: Boolean,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val auth by AuthManager.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val isAdmin = user.role.equals("admin", ignoreCase = true)  // target's role
    // Only admins change roles. Lead techs may delete non-admin members.
    val canDeleteThis = auth.isAdmin || (auth.isLeadTech && !isAdmin)

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            user.displayName.ifBlank { user.email },
            color = HFColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (user.email.isNotBlank()) {
            Text(
                user.email,
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.size(16.dp))

        // Role changes are admin-only.
        if (auth.isAdmin) {
            ActionRow(
                label = if (isAdmin) "Set Role: Tech" else "Set Role: Admin",
                destructive = false,
                enabled = !isSelf,
                onClick = {
                    val newRole = if (isAdmin) "tech" else "admin"
                    scope.launch { SharedStore.updateUserRole(user.id, newRole) }
                    onClose()
                }
            )
            Spacer(Modifier.size(8.dp))
        }
        // Lead techs can delete non-admin members; admins can delete anyone.
        if (canDeleteThis) {
            ActionRow(
                label = "Delete User…",
                destructive = true,
                enabled = !isSelf,
                onClick = { confirmDelete = true }
            )
        }
        if (isSelf) {
            Spacer(Modifier.size(8.dp))
            Text(
                "You can't change your own role or remove yourself. Ask another admin.",
                color = HFColors.OnSurface.copy(alpha = 0.50f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.size(12.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = HFColors.Background,
            titleContentColor = HFColors.OnSurface,
            textContentColor = HFColors.OnSurface.copy(alpha = 0.78f),
            title = { Text("Delete ${user.displayName}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This removes the user from the org. Their completed work logs and time entries are preserved for billing.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { SharedStore.deleteUser(user.id) }
                    onClose()
                }) {
                    Text("Yes, Remove", color = HFColors.StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = HFColors.OnSurface)
                }
            }
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    destructive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = when {
        !enabled -> HFColors.OnSurface.copy(alpha = 0.30f)
        destructive -> HFColors.StatusRed
        else -> HFColors.OnSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
