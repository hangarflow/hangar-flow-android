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
import com.hangarflow.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.OutlinedTextField

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
            color = HFColors.ink(0.55f),
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
        color = HFColors.ink(0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun UserRow(user: HFUserProfile, onTap: () -> Unit) {
    val isAdmin = user.role.equals("admin", ignoreCase = true)
    val accent = if (isAdmin) HFColors.StatusBlue else HFColors.fill(0.65f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HFColors.fill(0.04f))
            .border(1.dp, HFColors.stroke(0.08f), RoundedCornerShape(16.dp))
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
                color = HFColors.ink(0.55f),
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
                color = HFColors.ink(0.55f),
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
        // The certificate this person signs maintenance records with. Admin
        // only, like the role above it — and nobody sets their own anywhere,
        // because the signing trigger compares what a client sends against
        // this row, so a self-served number would defeat the whole check.
        if (auth.isAdmin) {
            CertificateBlock(user = user, scope = scope)
            Spacer(Modifier.size(8.dp))
        }

        // Lead techs can delete non-admin members; admins can delete anyone.
        if (canDeleteThis) {
            ActionRow(
                label = stringResource(R.string.us_delete_user),
                destructive = true,
                enabled = !isSelf,
                onClick = { confirmDelete = true }
            )
        }
        if (isSelf) {
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.us_cant_self),
                color = HFColors.ink(0.50f),
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
            textContentColor = HFColors.ink(0.78f),
            title = { Text("Delete ${user.displayName}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.us_remove_warning),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { SharedStore.deleteUser(user.id) }
                    onClose()
                }) {
                    Text(stringResource(R.string.us_yes_remove), color = HFColors.StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel), color = HFColors.OnSurface)
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
        !enabled -> HFColors.ink(0.30f)
        destructive -> HFColors.StatusRed
        else -> HFColors.OnSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.fill(0.04f))
            .border(1.dp, HFColors.stroke(0.08f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}


/**
 * Set a certificate from a tablet.
 *
 * The five kinds are the same list macOS and Windows offer, verbatim.
 * "A&P/IA" and "Repair Station" are the two strings canSignInspection tests
 * for, so a variant spelling here would silently stop someone signing an
 * annual with no error anywhere to explain it.
 */
@Composable
private fun CertificateBlock(
    user: HFUserProfile,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var editing by remember(user.id) { mutableStateOf(false) }
    var number by remember(user.id) { mutableStateOf(user.certNumber.orEmpty()) }
    var type by remember(user.id) { mutableStateOf(user.certType.orEmpty()) }
    var expires by remember(user.id) { mutableStateOf(user.certExpiresOn.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.fill(0.05f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.users_certificate),
                color = HFColors.ink(0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (!editing) {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.users_edit), color = HFColors.StatusBlue,
                         fontSize = 12.sp)
                }
            }
        }

        if (!editing) {
            Text(
                listOfNotNull(
                    user.certType?.ifBlank { null },
                    user.certNumber?.ifBlank { null },
                ).joinToString(" ").ifBlank { "—" },
                color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            if (!user.canSign) {
                Text(
                    stringResource(R.string.users_no_cert_cannot_sign),
                    color = HFColors.StatusOrange, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            user.certWarning?.let {
                Text(it, color = HFColors.StatusOrange, fontSize = 11.sp,
                     fontWeight = FontWeight.SemiBold)
            }
            if (user.canSign && !user.canSignInspection) {
                Text(
                    stringResource(R.string.users_cannot_sign_inspection),
                    color = HFColors.OnSurfaceMuted, fontSize = 11.sp,
                )
            }
        } else {
            OutlinedTextField(
                value = number, onValueChange = { number = it },
                label = { Text(stringResource(R.string.users_cert_number), fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("A&P", "A&P/IA", "Repairman", "Repair Station", "Pilot").forEach { opt ->
                    val on = type == opt
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(if (on) HFColors.StatusBlue else HFColors.fill(0.10f))
                            .clickable { type = if (on) "" else opt }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(
                            opt, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) HFColors.OnAccent else HFColors.OnSurface,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = expires, onValueChange = { expires = it },
                label = { Text(stringResource(R.string.users_cert_expires), fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = HFColors.StatusRed, fontSize = 11.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            val r = SharedStore.updateUserCertificate(
                                user.id, number, type, expires,
                            )
                            busy = false
                            if (r is SharedStore.CreateResult.Error) error = r.message
                            else editing = false
                        }
                    },
                ) {
                    Text(
                        if (busy) stringResource(R.string.users_saving)
                        else stringResource(R.string.users_save),
                        color = HFColors.StatusGreen,
                    )
                }
                TextButton(onClick = {
                    number = user.certNumber.orEmpty()
                    type = user.certType.orEmpty()
                    expires = user.certExpiresOn.orEmpty()
                    editing = false; error = null
                }) {
                    Text(stringResource(R.string.users_cancel), color = HFColors.OnSurfaceMuted)
                }
            }
        }
    }
}
