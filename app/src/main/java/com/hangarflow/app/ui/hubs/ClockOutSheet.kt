package com.hangarflow.app.ui.hubs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Full-screen sheet shown when a tech taps Clock Out. Captures:
 *   - daily work summary (lands on hf_time_entries.notes)
 *   - 0..N reimbursements with optional receipt photo (HEIC and JPEG
 *     both work — Bitmap re-encodes to JPEG before upload).
 *
 * On submit:
 *   1. Calls SharedStore.clockOutWithSummary which closes the shift
 *      and writes the time entry, returning its id.
 *   2. Uploads each receipt JPEG to the reimbursement-receipts bucket
 *      under {org}/{user}/{YYYY-MM}/{uuid}.jpg.
 *   3. Inserts hf_reimbursements rows linked to the new time entry.
 *
 * If any reimbursement upload fails, we surface the count but don't
 * roll back the shift — the entry is already saved and the tech can
 * re-add the receipt later from Hours/Payroll history.
 */
private data class PendingReimbursement(
    val id: String = UUID.randomUUID().toString(),
    var amountText: String = "",
    var description: String = "",
    var bitmap: Bitmap? = null
)

@Composable
fun ClockOutSheet(onDismiss: () -> Unit) {
    val shopState by SharedStore.state.collectAsState()
    val authState by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cloud = remember { HFCloudSyncService() }

    var summary by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<PendingReimbursement>() }
    var editingPhotoFor by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val libraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val rowId = editingPhotoFor ?: return@rememberLauncherForActivityResult
        editingPhotoFor = null
        if (uri != null) {
            val bmp = decodeUriToBitmap(context, uri)
            if (bmp != null) {
                val idx = rows.indexOfFirst { it.id == rowId }
                if (idx >= 0) rows[idx] = rows[idx].copy(bitmap = bmp)
            }
        }
    }

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
                Text("Clock Out", color = HFColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Log what you worked on and any reimbursements before closing the shift.",
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
                    .clickable(enabled = !submitting) { onDismiss() },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "WHAT DID YOU WORK ON TODAY?",
                    color = HFColors.OnSurface.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    placeholder = {
                        Text(
                            "e.g. NK123L: replaced #2 main tire IAW AMM 32-40-00, rigged speedbrake cable.",
                            color = HFColors.OnSurface.copy(alpha = 0.40f),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "REIMBURSEMENTS",
                        color = HFColors.OnSurface.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "+ Add",
                        color = HFColors.StatusGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { rows.add(PendingReimbursement()) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (rows.isEmpty()) {
                    Text(
                        "Tap +Add to log a receipt (parts, lunch run for the crew, parking, etc.). Receipt photos auto-delete after 30 days — admin should pull reports inside that window.",
                        color = HFColors.OnSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HFColors.OnSurface.copy(alpha = 0.03f))
                            .padding(12.dp)
                    )
                } else {
                    rows.forEachIndexed { idx, row ->
                        ReimbursementRow(
                            row = row,
                            onChangeAmount = { rows[idx] = row.copy(amountText = it) },
                            onChangeDescription = { rows[idx] = row.copy(description = it) },
                            onAttachPhoto = {
                                editingPhotoFor = row.id
                                libraryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onRemove = { rows.removeAt(idx) }
                        )
                    }
                }
            }

            errorMessage?.let { msg ->
                Text(msg, color = HFColors.StatusRed, fontSize = 12.sp)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    submitting = true
                    errorMessage = null
                    scope.launch {
                        val orgId = authState.orgId
                        val authUserId = runCatching {
                            SupabaseClientProvider.client.auth.currentUserOrNull()?.id
                        }.getOrNull()
                        if (orgId == null || authUserId == null) {
                            submitting = false
                            errorMessage = "Sign in expired. Sign out and back in."
                            return@launch
                        }
                        val userName = shopState.currentUser?.displayName ?: "Tech"

                        // Validate amounts before we close the shift so the
                        // tech can fix typos without losing their shift.
                        val cleanedRows = rows.filter {
                            it.amountText.isNotBlank() || it.description.isNotBlank() || it.bitmap != null
                        }
                        val invalid = cleanedRows.firstOrNull {
                            val v = it.amountText.replace("$", "").trim().toDoubleOrNull()
                            v == null || v <= 0
                        }
                        if (invalid != null) {
                            submitting = false
                            errorMessage = "Reimbursement amounts must be a positive number (e.g. 23.47)."
                            return@launch
                        }

                        val timeEntryId = SharedStore.clockOutWithSummary(summary)
                        if (timeEntryId == null) {
                            submitting = false
                            errorMessage = "No active shift to close."
                            return@launch
                        }

                        var failed = 0
                        for (row in cleanedRows) {
                            val cents = ((row.amountText.replace("$", "").trim().toDouble()) * 100).toInt()
                            val reimbursementId = UUID.randomUUID().toString()
                            val path: String? = row.bitmap?.let { bmp ->
                                runCatching {
                                    val jpegBytes = withContext(Dispatchers.IO) {
                                        val stream = ByteArrayOutputStream()
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 78, stream)
                                        stream.toByteArray()
                                    }
                                    cloud.uploadReceiptPhoto(
                                        jpeg = jpegBytes,
                                        orgId = orgId,
                                        userAuthId = authUserId,
                                        reimbursementId = reimbursementId
                                    )
                                }.getOrElse {
                                    failed++
                                    null
                                }
                            }
                            runCatching {
                                cloud.createReimbursement(
                                    id = reimbursementId,
                                    orgId = orgId,
                                    userId = authUserId,
                                    userName = userName,
                                    amountCents = cents,
                                    description = row.description.trim(),
                                    receiptStoragePath = path,
                                    timeEntryId = timeEntryId,
                                    sourceDevice = SharedStore.deviceIdentifier()
                                )
                            }.onFailure { failed++ }
                        }

                        submitting = false
                        if (failed > 0) {
                            errorMessage = "Shift saved, but $failed reimbursement${if (failed == 1) "" else "s"} failed to upload."
                        } else {
                            onDismiss()
                        }
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HFColors.StatusRed,
                    disabledContainerColor = HFColors.OnSurface.copy(alpha = 0.10f)
                )
            ) {
                if (submitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Clock Out", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReimbursementRow(
    row: PendingReimbursement,
    onChangeAmount: (String) -> Unit,
    onChangeDescription: (String) -> Unit,
    onAttachPhoto: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$", color = HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = row.amountText,
                onValueChange = onChangeAmount,
                placeholder = { Text("0.00", color = HFColors.OnSurface.copy(alpha = 0.4f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
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
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove",
                tint = HFColors.StatusRed.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp).clickable { onRemove() }
            )
        }
        OutlinedTextField(
            value = row.description,
            onValueChange = onChangeDescription,
            placeholder = { Text("What was this for? (parts, lunch, parking…)", color = HFColors.OnSurface.copy(alpha = 0.4f)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Receipt thumbnail",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                )
                Text(
                    "Replace photo",
                    color = HFColors.StatusBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAttachPhoto() }
                )
            } ?: run {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HFColors.StatusBlue.copy(alpha = 0.12f))
                        .clickable { onAttachPhoto() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = HFColors.StatusBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add receipt photo", color = HFColors.StatusBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun decodeUriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}
