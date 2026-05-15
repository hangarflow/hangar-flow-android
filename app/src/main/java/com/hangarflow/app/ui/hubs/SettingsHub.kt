package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.IssueReporter
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

@Composable
fun SettingsHub() {
    val authState by AuthManager.state.collectAsState()
    val shopState by SharedStore.state.collectAsState()
    val context = LocalContext.current
    val packageInfo = remember_packageInfo(context)
    var confirmSignOut by remember { mutableStateOf(false) }
    var showReportIssue by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsCard(title = "Shop") {
            Row(label = "Organization", value = authState.orgName)
            Row(label = "Your role", value = authState.role.replaceFirstChar { it.titlecase() })
            shopState.currentUser?.let { me ->
                Row(label = "Display name", value = me.displayName.ifBlank { "—" })
                Row(label = "Email", value = me.email.ifBlank { "—" })
                Row(label = "Initials", value = me.initials.ifBlank { "—" })
            }
        }
        Spacer(Modifier.size(14.dp))

        SettingsCard(title = "Sync") {
            Row(
                label = "Backend",
                value = if (shopState.loading) "Checking…" else "Connected"
            )
            Row(label = "Planes", value = "${shopState.planes.size}")
            Row(label = "Work logs", value = "${shopState.workLogs.size}")
            Row(label = "Squawks", value = "${shopState.squawks.size}")
            Row(label = "Manuals", value = "${shopState.manuals.size}")
            Row(label = "Part requests", value = "${shopState.partRequests.size}")
            Row(label = "Time entries", value = "${shopState.timeEntries.size}")
        }
        Spacer(Modifier.size(14.dp))

        SettingsCard(title = "About") {
            Row(label = "App", value = "Hangar Flow")
            Row(label = "Version", value = packageInfo)
            Row(label = "Device ID", value = SharedStore.deviceIdentifier().take(8))
        }
        Spacer(Modifier.size(14.dp))

        SettingsCard(title = "Account") {
            ChangePasswordRow()
            Spacer(Modifier.size(2.dp))
            ChangeEmailRow(currentEmail = shopState.currentUser?.email.orEmpty())
        }
        Spacer(Modifier.size(14.dp))

        SettingsCard(title = "Support") {
            LinkRow(label = "Report an Issue") { showReportIssue = true }
        }
        Spacer(Modifier.size(14.dp))

        SettingsCard(title = "Legal") {
            LinkRow(label = "Privacy Policy") {
                openUrl(context, "https://hangarflow.com/privacy")
            }
            LinkRow(label = "Terms of Service") {
                openUrl(context, "https://hangarflow.com/terms")
            }
        }

        Spacer(Modifier.size(20.dp))

        // Sign out
        SignOutButton { confirmSignOut = true }

        Spacer(Modifier.size(40.dp))
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = HFColors.Background,
            titleContentColor = HFColors.OnSurface,
            textContentColor = HFColors.OnSurface.copy(alpha = 0.78f),
            title = { Text("Sign out?") },
            text = { Text("You'll be returned to the login screen and live sync will stop until you sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    AuthManager.signOut()
                }) { Text("Sign out", color = HFColors.StatusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text("Cancel", color = HFColors.OnSurface)
                }
            }
        )
    }

    if (showReportIssue) {
        ReportIssueDialog(onDismiss = { showReportIssue = false })
    }
}

@Composable
private fun ReportIssueDialog(onDismiss: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = HFColors.Background,
        titleContentColor = HFColors.OnSurface,
        textContentColor = HFColors.OnSurface.copy(alpha = 0.78f),
        title = {
            Text(
                if (sent) "Thanks — we'll take a look" else "Report an Issue",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (sent) {
                Text(
                    "Your report was sent to the Hangar Flow team. If we need more info, we'll reply to your account email.",
                    fontSize = 13.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tell us what went wrong or what you'd like to see. Your name, email, and app version are included automatically.",
                        color = HFColors.OnSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it.take(120) },
                        label = { Text("Subject") },
                        singleLine = true,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it.take(4000) },
                        label = { Text("Description") },
                        enabled = !sending,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                    if (error != null) {
                        Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = HFColors.StatusGreen, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    enabled = !sending && subject.isNotBlank() && body.isNotBlank(),
                    onClick = {
                        sending = true; error = null
                        scope.launch {
                            when (val r = IssueReporter.submit(subject, body)) {
                                IssueReporter.Result.Success -> { sent = true; sending = false }
                                is IssueReporter.Result.Error -> { error = r.message; sending = false }
                            }
                        }
                    }
                ) {
                    Text(
                        if (sending) "Sending…" else "Send",
                        color = HFColors.StatusGreen, fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(onClick = onDismiss, enabled = !sending) {
                    Text("Cancel", color = HFColors.OnSurface)
                }
            }
        }
    )
}

@Composable
private fun remember_packageInfo(context: android.content.Context): String {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (_: Throwable) {
        "—"
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text(
            title.uppercase(),
            color = HFColors.OnSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.size(10.dp))
        content()
    }
}

@Composable
private fun Row(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 13.sp)
        Text(
            value,
            color = HFColors.OnSurface.copy(alpha = 0.70f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = HFColors.OnSurface, fontSize = 13.sp)
        Text(
            "Open ›",
            color = HFColors.StatusBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HFColors.StatusRed.copy(alpha = 0.12f))
            .border(1.dp, HFColors.StatusRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Sign out",
            color = HFColors.StatusRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Change Password ───
@Composable
private fun ChangePasswordRow() {
    var expanded by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (success) success = false
                    expanded = !expanded
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Change Password", color = HFColors.OnSurface, fontSize = 13.sp)
            Text(
                if (success) "Updated ✓" else if (expanded) "Cancel" else "Edit",
                color = if (success) HFColors.StatusGreen else HFColors.StatusBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (expanded && !success) {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; error = null },
                label = { Text("New password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text("Confirm new password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.size(6.dp))
                Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp)
            }
            Spacer(Modifier.size(10.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HFColors.StatusGreen.copy(alpha = 0.18f))
                    .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !saving && newPassword.isNotBlank() && confirmPassword.isNotBlank()) {
                        when {
                            newPassword.length < 8 -> error = "Password must be at least 8 characters."
                            newPassword != confirmPassword -> error = "Passwords don't match."
                            else -> {
                                saving = true
                                error = null
                                scope.launch {
                                    when (val r = AuthManager.updatePassword(newPassword)) {
                                        AuthManager.UpdateResult.Success -> {
                                            success = true; expanded = false
                                            newPassword = ""; confirmPassword = ""
                                        }
                                        is AuthManager.UpdateResult.Error -> error = r.message
                                    }
                                    saving = false
                                }
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (saving) "Saving…" else "Update Password",
                    color = HFColors.StatusGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(6.dp))
        }
    }
}

// ─── Change Email ───
@Composable
private fun ChangeEmailRow(currentEmail: String) {
    var expanded by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (pendingEmail != null) pendingEmail = null
                    expanded = !expanded
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Change Email", color = HFColors.OnSurface, fontSize = 13.sp)
                Spacer(Modifier.size(2.dp))
                Text(
                    if (pendingEmail != null) "Pending: confirm via $pendingEmail"
                    else currentEmail.ifBlank { "—" },
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                if (pendingEmail != null) "Sent ✓" else if (expanded) "Cancel" else "Edit",
                color = if (pendingEmail != null) HFColors.StatusGreen else HFColors.StatusBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (expanded && pendingEmail == null) {
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it; error = null },
                label = { Text("New email") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            if (error != null) {
                Spacer(Modifier.size(6.dp))
                Text(error!!, color = HFColors.StatusRed, fontSize = 12.sp)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "We'll send a confirmation link to the new address. The change takes effect once you click that link.",
                color = HFColors.OnSurface.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
            Spacer(Modifier.size(10.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HFColors.StatusBlue.copy(alpha = 0.18f))
                    .border(1.dp, HFColors.StatusBlue.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !saving && newEmail.isNotBlank()) {
                        saving = true
                        error = null
                        scope.launch {
                            when (val r = AuthManager.updateEmail(newEmail)) {
                                AuthManager.UpdateResult.Success -> {
                                    pendingEmail = newEmail.trim(); expanded = false; newEmail = ""
                                }
                                is AuthManager.UpdateResult.Error -> error = r.message
                            }
                            saving = false
                        }
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (saving) "Sending…" else "Send Verification",
                    color = HFColors.StatusBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(6.dp))
        }
    }
}
