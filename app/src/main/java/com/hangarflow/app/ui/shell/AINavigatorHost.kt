package com.hangarflow.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.ui.home.HomeDestination
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * AI navigator dialogs — the one-time "AI can be wrong" disclaimer and the
 * "What are you looking for?" input. Routes a plain-language request to a
 * hub via the `ai-navigate` edge function (offline to the app: the model
 * only ever sees the destination catalog, never the web).
 */
@Composable
fun AINavigatorHost(
    show: Boolean,
    showDisclaimer: Boolean,
    isElevated: Boolean,
    onDismiss: () -> Unit,
    onDismissDisclaimer: () -> Unit,
    onAcceptDisclaimer: () -> Unit,
    onNavigate: (HomeDestination) -> Unit
) {
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = onDismissDisclaimer,
            title = { Text("Heads up — AI can be wrong", fontWeight = FontWeight.Bold, color = HFColors.OnSurface) },
            text = {
                Text(
                    "This assistant points you to the right screen from a plain-language request. It can make mistakes — always double-check important actions yourself.",
                    color = HFColors.OnSurface.copy(alpha = 0.75f), fontSize = 13.sp
                )
            },
            confirmButton = { TextButton(onClick = onAcceptDisclaimer) { Text("I understand", color = HFColors.StatusCyan, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = onDismissDisclaimer) { Text("Cancel", color = HFColors.OnSurface.copy(alpha = 0.6f)) } },
            containerColor = HFColors.Background
        )
    }

    if (show) {
        val cloud = remember { HFCloudSyncService() }
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var note by remember { mutableStateOf<String?>(null) }
        var noMatch by remember { mutableStateOf(false) }
        val catalog = remember(isElevated) { buildAINavCatalog(isElevated) }

        fun run() {
            val q = query.trim()
            if (q.isEmpty() || loading) return
            loading = true; note = null; noMatch = false
            scope.launch {
                val result = runCatching { cloud.aiNavigate(q, catalog) }.getOrNull()
                loading = false
                val dest = result?.key?.let { keyToDestination(it) }
                if (dest != null) onNavigate(dest)
                else { noMatch = true; note = result?.note ?: "I couldn't find that — try rephrasing." }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("What are you looking for?", fontWeight = FontWeight.Bold, color = HFColors.OnSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("e.g. “add a user” or “my hours”", color = HFColors.OnSurface.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = HFColors.OnSurface, unfocusedTextColor = HFColors.OnSurface,
                            cursorColor = HFColors.OnSurface,
                            focusedBorderColor = HFColors.StatusCyan, unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.2f)
                        )
                    )
                    if (loading) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = HFColors.StatusCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Finding it…", color = HFColors.OnSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    } else if (note != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(note!!, color = if (noMatch) HFColors.StatusOrange else HFColors.OnSurface.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("The AI can be wrong — double-check important actions.", color = HFColors.OnSurface.copy(alpha = 0.35f), fontSize = 10.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { run() }, enabled = query.trim().isNotEmpty() && !loading) {
                    Text("Go", color = HFColors.StatusCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = HFColors.OnSurface.copy(alpha = 0.6f)) } },
            containerColor = HFColors.Background
        )
    }
}

private fun buildAINavCatalog(elevated: Boolean): List<HFCloudSyncService.NavDestination> {
    val rows = mutableListOf(
        HFCloudSyncService.NavDestination("time", "Hours / Time Card", "my hours, clock in or out, timecard, payroll"),
        HFCloudSyncService.NavDestination("tasks", "Tasks", "tasks, to-dos, assignments"),
        HFCloudSyncService.NavDestination("planes", "Planes", "aircraft list, view a plane, tail numbers"),
        HFCloudSyncService.NavDestination("workLogs", "Work Logs", "work logs, jobs"),
        HFCloudSyncService.NavDestination("squawks", "Squawks", "discrepancies, gripes, write-ups"),
        HFCloudSyncService.NavDestination("findParts", "Find Parts", "search for a part, part number, component, ATA"),
        HFCloudSyncService.NavDestination("manuals", "Manuals", "aircraft manuals, documents, PDFs"),
        HFCloudSyncService.NavDestination("partLocations", "Part Locations", "where a part lives, part photos, bins"),
        HFCloudSyncService.NavDestination("schedule", "Schedule", "calendar, events, time off"),
        HFCloudSyncService.NavDestination("activityLog", "Activity Log", "history, who did what, audit trail"),
        HFCloudSyncService.NavDestination("settings", "Settings", "settings, account, sign out")
    )
    if (elevated) rows.add(HFCloudSyncService.NavDestination("users", "Users / Roster", "add a user, invite, manage team, roles, technicians"))
    return rows
}

private fun keyToDestination(key: String): HomeDestination? = when (key) {
    "time" -> HomeDestination.TimeCard
    "tasks" -> HomeDestination.Tasks
    "planes" -> HomeDestination.Planes
    "workLogs" -> HomeDestination.WorkLogs
    "squawks" -> HomeDestination.Squawks
    "findParts" -> HomeDestination.FindParts
    "manuals" -> HomeDestination.Manuals
    "partLocations" -> HomeDestination.PartLocations
    "schedule" -> HomeDestination.Schedule
    "activityLog" -> HomeDestination.ActivityLog
    "users" -> HomeDestination.Users
    "settings" -> HomeDestination.Settings
    else -> null
}
