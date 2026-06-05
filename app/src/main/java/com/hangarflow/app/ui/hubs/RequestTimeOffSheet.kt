package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full-screen sheet to submit a PTO request. Picks start/end dates and
 * an optional reason, then inserts into `hf_time_off_requests` with
 * status `pending`. An admin approves/denies later from desktop or iOS
 * admin UI. Mirrors iOS `IOSRequestTimeOffSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestTimeOffSheet(onDismiss: () -> Unit) {
    val shopState by SharedStore.state.collectAsState()
    val authState by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val cloud = remember { HFCloudSyncService() }

    val today = LocalDate.now()
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var reason by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val canSave = authState.orgId != null && !isSaving && !endDate.isBefore(startDate)
    val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val prettyFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Request Time Off", color = HFColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pick a date range and optional reason. An admin approves it.",
                    color = HFColors.OnSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.10f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = HFColors.OnSurface, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DateField(
                label = "Start date",
                value = startDate.format(prettyFormatter),
                onClick = { showStartPicker = true }
            )
            DateField(
                label = "End date",
                value = endDate.format(prettyFormatter),
                onClick = { showEndPicker = true }
            )

            Text("Reason (optional)", color = HFColors.OnSurface.copy(alpha = 0.68f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text("e.g. vacation, doctor", color = HFColors.OnSurface.copy(alpha = 0.40f), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
                    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
                    focusedTextColor = HFColors.OnSurface,
                    unfocusedTextColor = HFColors.OnSurface,
                    cursorColor = HFColors.OnSurface
                )
            )

            if (endDate.isBefore(startDate)) {
                Text("End date must be on or after start date.", color = HFColors.StatusRed, fontSize = 12.sp)
            }

            saveError?.let {
                Text(it, color = HFColors.StatusRed, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    val orgId = authState.orgId ?: return@Button
                    val user = shopState.currentUser ?: shopState.users.firstOrNull()
                    if (user == null) {
                        saveError = "Couldn't resolve your profile. Sign out and back in."
                        return@Button
                    }
                    val userId = user.id
                    val userName = user.displayName
                    isSaving = true
                    saveError = null
                    val isAdmin = authState.isAdmin
                    scope.launch {
                        runCatching {
                            cloud.createTimeOffRequest(
                                orgId = orgId,
                                userId = userId,
                                userName = userName,
                                startDateIso = startDate.format(isoFormatter),
                                endDateIso = endDate.format(isoFormatter),
                                reason = reason.trim(),
                                sourceDevice = SharedStore.deviceIdentifier(),
                                autoApprove = isAdmin
                            )
                        }.onSuccess {
                            isSaving = false
                            onDismiss()
                        }.onFailure {
                            isSaving = false
                            saveError = it.message ?: "Couldn't submit request."
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HFColors.StatusBlue,
                    disabledContainerColor = HFColors.OnSurface.copy(alpha = 0.10f)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit Request", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        startDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (endDate.isBefore(startDate)) endDate = startDate
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        endDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateField(label: String, value: String, onClick: () -> Unit) {
    Column {
        Text(label, color = HFColors.OnSurface.copy(alpha = 0.68f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.04f))
                .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = HFColors.OnSurface.copy(alpha = 0.65f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(value, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
