package com.hangarflow.app.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.cloud.HFLogbookEntry
import com.hangarflow.app.data.cloud.LogbookCloud
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.compose.ui.res.stringResource
import com.hangarflow.app.R

/**
 * The aircraft's permanent record, on Android.
 *
 * Read and sign — not author. A logbook entry is a legal document and writing
 * one properly needs the AI draft, the times, the airworthiness finding and a
 * keyboard; that belongs on a desktop. What belongs on the tablet in the hangar
 * is the other half: reading what the book already says, and signing the work
 * you just did before you walk away from the aeroplane.
 *
 * The certificate comes from the signer's own profile and nothing else. There
 * is deliberately no field anywhere in this file to type a certificate number
 * into, because a typed certificate number is not a signature — and the
 * database checks it independently anyway.
 */
@Composable
fun LogbookScreen(
    orgId: String,
    planeId: String,
    planeTail: String,
    signerAuthUserId: String?,
    signerName: String?,
    signerCertNumber: String?,
    signerCertType: String?,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember(planeId) { mutableStateOf<List<HFLogbookEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<HFLogbookEntry?>(null) }

    suspend fun reload() {
        entries = withContext(Dispatchers.IO) {
            runCatching { LogbookCloud.fetchEntries(orgId) }.getOrDefault(emptyList())
        }.filter { it.planeId == planeId }
        loading = false
    }

    LaunchedEffect(planeId) { reload() }

    Column(Modifier.fillMaxSize().background(HFColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lb_logbook), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = HFColors.OnSurface)
                Text(planeTail.uppercase(), fontSize = 12.sp, color = HFColors.OnSurfaceMuted)
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.lb_close), color = HFColors.OnSurfaceMuted) }
        }
        Divider(color = HFColors.fill(0.10f))

        note?.let {
            Text(it, fontSize = 12.sp, color = HFColors.StatusOrange,
                modifier = Modifier.padding(16.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HFColors.StatusCyan)
            }
            entries.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No logbook entries yet for ${planeTail.uppercase()}",
                    fontSize = 14.sp, color = HFColors.OnSurfaceMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.lb_an_entry_records_a_discrepancy_and_what_was),
                    fontSize = 12.sp, color = HFColors.ink(0.45f),
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { e ->
                    EntryCard(e) { open = e }
                }
            }
        }
    }

    open?.let { entry ->
        val live = entries.firstOrNull { it.id == entry.id } ?: entry
        EntrySheet(
            entry = live,
            canSign = !live.isSigned && !signerCertNumber.isNullOrBlank(),
            signerName = signerName,
            certType = signerCertType,
            certNumber = signerCertNumber,
            busy = busy,
            onDismiss = { open = null },
            onSign = {
                scope.launch {
                    busy = true
                    val msg = withContext(Dispatchers.IO) {
                        LogbookCloud.sign(
                            entry = live,
                            signerAuthUserId = signerAuthUserId,
                            signerName = signerName.orEmpty(),
                            certNumber = signerCertNumber,
                            certType = signerCertType,
                            nowIso = Instant.now().toString(),
                        )
                    }
                    busy = false
                    note = msg
                    if (msg == null) { reload(); open = null }
                }
            },
        )
    }
}

@Composable
private fun EntryCard(e: HFLogbookEntry, onOpen: () -> Unit) {
    val tint = when {
        e.isVoid -> HFColors.ink(0.40f)
        e.isSigned -> HFColors.StatusGreen
        else -> HFColors.StatusOrange
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(HFColors.fill(0.05f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.statusLabel.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = tint)
            Spacer(Modifier.width(8.dp))
            Text(e.typeLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = HFColors.ink(0.70f), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(e.entryDate, fontSize = 11.sp, color = HFColors.ink(0.45f))
        }
        Text(e.logSection.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = HFColors.ink(0.40f))
        e.reportedDiscrepancy?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = HFColors.ink(0.85f), maxLines = 2)
        }
        Text(e.body, fontSize = 12.sp, color = HFColors.OnSurfaceMuted, maxLines = 3)
        e.signedByName?.let {
            Text(it, fontSize = 11.sp, color = HFColors.ink(0.50f))
        }
    }
}

@Composable
private fun EntrySheet(
    entry: HFLogbookEntry,
    canSign: Boolean,
    signerName: String?,
    certType: String?,
    certNumber: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSign: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (!entry.isSigned) {
                TextButton(onClick = onSign, enabled = canSign && !busy) {
                    Text(if (busy) "Signing…" else "Sign and return to service")
                }
            } else TextButton(onClick = onDismiss) { Text(stringResource(R.string.lb_close_2)) }
        },
        dismissButton = {
            if (!entry.isSigned) TextButton(onClick = onDismiss) { Text(stringResource(R.string.lb_not_yet)) }
        },
        title = { Text(if (entry.isSigned) "Logbook entry" else "Draft entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${entry.typeLabel} · ${entry.logSection.label} · ${entry.entryDate}",
                    fontSize = 12.sp, color = HFColors.OnSurfaceMuted)
                entry.reportedDiscrepancy?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.lb_discrepancy), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = HFColors.ink(0.45f))
                    Text(it, fontSize = 13.sp, color = HFColors.OnSurface)
                }
                Text(stringResource(R.string.lb_corrective_action), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = HFColors.ink(0.45f))
                Text(entry.body, fontSize = 13.sp, color = HFColors.OnSurface)

                entry.performedByName?.takeIf { it.isNotBlank() }?.let {
                    // 43.9(a)(3): the performer is named when it is not the signer.
                    Text("Work performed by $it", fontSize = 11.sp,
                        color = HFColors.ink(0.60f))
                }

                entry.signatureBlock?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Verified, null, tint = HFColors.StatusGreen,
                            modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = HFColors.ink(0.85f))
                    }
                }

                if (!entry.isSigned) {
                    if (canSign) {
                        Text(
                            "Signing as $signerName — ${certType.orEmpty()} ${certNumber.orEmpty()}",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = HFColors.ink(0.80f),
                        )
                        if (entry.entryType.needsIA &&
                            certType != "A&P/IA" && certType != "Repair Station"
                        ) {
                            Text(
                                stringResource(R.string.lb_an_annual_or_progressive_must_be_signed_by_a,
                                    certType ?: "no certificate"),
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = HFColors.StatusRed,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.lb_you_have_no_certificate_on_file_so_you_can_r),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = HFColors.StatusOrange,
                        )
                    }
                    Text(
                        stringResource(R.string.lb_signing_writes_this_into_the_aircraft_s_perm),
                        fontSize = 10.sp, color = HFColors.ink(0.45f),
                    )
                }
            }
        },
    )
}
