package com.hangarflow.app.ui.receiving

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.cloud.HFPartRelease
import com.hangarflow.app.data.cloud.PartReleaseCloud
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.compose.ui.res.stringResource
import com.hangarflow.app.R

/**
 * Receiving inspection, on Android.
 *
 * What this is for is 14 CFR 43.13: a mechanic has to determine a part is
 * acceptable BEFORE installing it, and the determination is theirs — not the
 * vendor's and not this software's. So the screen records what arrived and who
 * accepted it. It never decides.
 *
 * The distinction the feature turns on, and the one shops get wrong: **not
 * every part needs an 8130-3.** Standard AN/MS/NAS hardware never has one and
 * never will. Treating a missing tag as a failure is how shops learn to fake
 * them, so the exceptions are first-class — each simply has to say why.
 *
 * Android is a good home for the accepting half: the part is in your hands at
 * the bench, and the decision takes one tap. Creating a release from a
 * photographed certificate stays on the desktop for now.
 */
@Composable
fun ReceivingScreen(orgId: String, actorId: String?, actorName: String?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<HFPartRelease>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf<HFPartRelease?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    suspend fun reload() {
        releases = withContext(Dispatchers.IO) {
            runCatching { PartReleaseCloud.fetch(orgId) }.getOrDefault(emptyList())
        }
        loading = false
    }
    // Re-fetch when another device records or accepts a part. The counter only moves on a
    // realtime event, and only a mounted screen is listening, so an
    // unopened list costs nothing.
    val releaseRev by com.hangarflow.app.data.cloud.HFFeatureEvents.partRelease.collectAsState()
    LaunchedEffect(orgId, releaseRev) { reload() }

    Column(Modifier.fillMaxSize().background(HFColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rcv_receiving), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = HFColors.OnSurface)
                Text(
                    "${releases.count { it.isPending }} awaiting inspection",
                    fontSize = 12.sp, color = HFColors.OnSurfaceMuted,
                )
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.rcv_close), color = HFColors.OnSurfaceMuted) }
        }
        Divider(color = HFColors.fill(0.10f))

        note?.let {
            Text(it, fontSize = 12.sp, color = HFColors.StatusOrange,
                modifier = Modifier.padding(16.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HFColors.StatusCyan)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(releases, key = { it.id }) { r ->
                    ReleaseCard(
                        r = r,
                        enabled = !busy,
                        onAccept = {
                            scope.launch {
                                busy = true
                                val msg = withContext(Dispatchers.IO) {
                                    PartReleaseCloud.accept(
                                        r, actorId, actorName, Instant.now().toString(),
                                    )
                                }
                                busy = false
                                note = msg
                                if (msg == null) reload()
                            }
                        },
                        onReject = { rejecting = r; rejectReason = "" },
                    )
                }
            }
        }
    }

    rejecting?.let { r ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Reject ${r.partNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        // Kept, never deleted: "we sent one back" is exactly
                        // what a shop needs to be able to prove later.
                        "The record stays on file either way — a rejection is evidence.",
                        fontSize = 12.sp, color = HFColors.OnSurfaceMuted,
                    )
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text(stringResource(R.string.rcv_why_it_s_going_back), fontSize = 12.sp) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectReason.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val msg = withContext(Dispatchers.IO) {
                                PartReleaseCloud.reject(
                                    r, rejectReason, Instant.now().toString(),
                                )
                            }
                            busy = false
                            note = msg
                            if (msg == null) { rejecting = null; reload() }
                        }
                    },
                ) { Text(stringResource(R.string.rcv_reject)) }
            },
            dismissButton = {
                TextButton(onClick = { rejecting = null }) { Text(stringResource(R.string.rcv_cancel)) }
            },
        )
    }
}

@Composable
private fun ReleaseCard(
    r: HFPartRelease,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val tint = when {
        r.isRejected -> HFColors.StatusRed
        r.isOutOfLife -> HFColors.StatusOrange
        r.isAccepted -> HFColors.StatusGreen
        else -> HFColors.StatusCyan
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(HFColors.fill(0.05f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.partNumber, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = HFColors.OnSurface)
            Spacer(Modifier.weight(1f))
            Text(r.statusLabel.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = tint)
        }
        r.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = HFColors.OnSurfaceMuted, maxLines = 2)
        }
        Text(r.docType.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = HFColors.ink(0.70f))
        Text(r.docType.hint, fontSize = 11.sp, color = HFColors.ink(0.45f))

        // The paste-into-the-logbook line, composed here so nobody retypes a
        // part number at five o'clock.
        Text(r.traceLine, fontSize = 11.sp, color = HFColors.ink(0.60f))

        if (r.isOutOfLife) {
            Text(
                "Past its shelf life (${r.shelfLifeExpiresOn}). Out of life is out of life, " +
                    "however good the tag was.",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = HFColors.StatusOrange,
            )
        }
        r.rejectedReason?.takeIf { it.isNotBlank() }?.let {
            Text("Rejected: $it", fontSize = 11.sp, color = HFColors.StatusRed)
        }
        r.acceptedByName?.takeIf { it.isNotBlank() }?.let {
            Text("Accepted by $it", fontSize = 11.sp, color = HFColors.ink(0.55f))
        }

        if (r.isPending) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(HFColors.fill(0.10f))
                        .clickable(enabled = enabled) { onReject() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.rcv_reject_2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = HFColors.OnSurface) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(
                            if (r.isOutOfLife) HFColors.fill(0.10f) else HFColors.StatusGreen
                        )
                        // An out-of-life part cannot be accepted at all — the
                        // data layer refuses it too, so this is a courtesy, not
                        // the control.
                        .clickable(enabled = enabled && !r.isOutOfLife) { onAccept() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.rcv_accept), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (r.isOutOfLife) HFColors.ink(0.40f) else HFColors.OnAccent,
                    )
                }
            }
        }
    }
}
