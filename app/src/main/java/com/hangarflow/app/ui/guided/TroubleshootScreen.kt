package com.hangarflow.app.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.cloud.HFCauseOutcome
import com.hangarflow.app.data.cloud.HFTroubleCause
import com.hangarflow.app.data.cloud.HFTroubleshootRun
import com.hangarflow.app.data.cloud.TroubleshootCloud
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.compose.ui.res.stringResource
import com.hangarflow.app.R

/**
 * Chasing a fault, on Android.
 *
 * Every probable cause is on screen at once and in the MANUAL'S ORDER. Nothing
 * is ranked, nothing is highlighted as "most likely", nothing is pre-selected.
 * That posture is the feature: the aeroplane is in front of the mechanic and
 * the manual is not a detective. Software that guesses which cause is likeliest
 * would be inventing an opinion the manufacturer did not express.
 *
 * The other half is what it records. "We checked X and Y, both good" is the
 * thing that never gets written down and the thing the next person most needs,
 * so stopping without finding the fault demands a reason, and the elimination
 * summary is composed for the logbook rather than left to a tired human at 5pm.
 */
@Composable
fun TroubleshootScreen(
    run: HFTroubleshootRun,
    onOpenManual: ((manual: String, page: Int) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var live by remember(run.id) { mutableStateOf(run) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var stopReason by remember { mutableStateOf("") }
    val notes = remember { mutableStateMapOf<Int, String>() }

    fun mark(cause: HFTroubleCause, outcome: HFCauseOutcome) {
        val n = cause.n ?: return
        scope.launch {
            busy = true
            val msg = withContext(Dispatchers.IO) {
                TroubleshootCloud.setOutcome(
                    live, n, outcome, notes[n], null, Instant.now().toString(),
                )
            }
            busy = false
            note = msg
            if (msg == null) {
                notes.remove(n)
                live = live.copy(
                    causes = live.causes.map {
                        if (it.n == n) it.copy(outcome = outcome.raw, note = notes[n] ?: it.note)
                        else it
                    },
                    foundCause = if (outcome == HFCauseOutcome.CONFIRMED) n else live.foundCause,
                    closedAt = if (outcome == HFCauseOutcome.CONFIRMED)
                        Instant.now().toString() else live.closedAt,
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HFColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ts_troubleshoot), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = HFColors.OnSurface)
                Text(live.symptom, fontSize = 12.sp, color = HFColors.OnSurfaceMuted, maxLines = 2)
            }
            Text(
                "${live.ruledOut}/${live.causes.size}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HFColors.StatusCyan,
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.ts_close), color = HFColors.OnSurfaceMuted) }
        }
        Divider(color = HFColors.fill(0.10f))

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            note?.let {
                item { Text(it, fontSize = 12.sp, color = HFColors.StatusOrange) }
            }
            live.confirmed?.let { hit -> item { FoundPanel(hit) } }
            item { Banner(live) }
            items(live.causes, key = { it.n ?: it.cause.hashCode() }) { c ->
                CauseCard(
                    cause = c,
                    closed = live.isClosed,
                    enabled = !busy,
                    draft = notes[c.n ?: -1].orEmpty(),
                    onDraft = { notes[c.n ?: -1] = it },
                    onOpenManual = onOpenManual,
                    onMark = { mark(c, it) },
                )
            }
            live.eliminationSummary?.let { s -> item { SummaryPanel(s) } }
            if (live.confirmed == null && !live.isClosed) {
                item {
                    StopBlock(
                        stopping = stopping,
                        reason = stopReason,
                        busy = busy,
                        onReason = { stopReason = it },
                        onBegin = { stopping = true },
                        onCancel = { stopping = false },
                        onStop = {
                            scope.launch {
                                busy = true
                                val msg = withContext(Dispatchers.IO) {
                                    TroubleshootCloud.close(
                                        live, stopReason, Instant.now().toString(),
                                    )
                                }
                                busy = false
                                note = msg
                                if (msg == null) {
                                    live = live.copy(
                                        closedAt = Instant.now().toString(),
                                        closedReason = stopReason,
                                    )
                                    stopping = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(run: HFTroubleshootRun) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(HFColors.fill(0.05f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${run.causes.size} probable cause${if (run.causes.size == 1) "" else "s"}, " +
                "in your manual's order",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HFColors.ink(0.55f),
        )
        run.cautions.forEach {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = HFColors.StatusOrange,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(it, fontSize = 12.sp, color = HFColors.StatusOrange)
            }
        }
        if (run.gaps.isNotEmpty()) {
            Text(stringResource(R.string.ts_your_manuals_don_t_cover), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = HFColors.StatusOrange)
            run.gaps.forEach {
                Text("· $it", fontSize = 11.sp, color = HFColors.OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun CauseCard(
    cause: HFTroubleCause,
    closed: Boolean,
    enabled: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onOpenManual: ((String, Int) -> Unit)?,
    onMark: (HFCauseOutcome) -> Unit,
) {
    val state = cause.state
    val tint = when (state) {
        HFCauseOutcome.CONFIRMED -> HFColors.StatusGreen
        HFCauseOutcome.RULED_OUT -> HFColors.ink(0.40f)
        else -> HFColors.StatusCyan
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(
                if (state == HFCauseOutcome.CONFIRMED)
                    HFColors.StatusGreen.copy(alpha = 0.08f) else HFColors.fill(0.04f)
            )
            .border(
                1.dp,
                if (state == HFCauseOutcome.CONFIRMED)
                    HFColors.StatusGreen.copy(alpha = 0.30f) else HFColors.fill(0.10f),
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Text("${cause.n ?: 0}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = HFColors.OnAccent)
            }
            Spacer(Modifier.width(8.dp))
            if (state != HFCauseOutcome.UNTESTED) {
                Text(
                    if (state == HFCauseOutcome.CONFIRMED) "THAT WAS IT" else "RULED OUT",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint,
                )
            }
        }

        Text(
            cause.cause,
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (state == HFCauseOutcome.RULED_OUT)
                HFColors.ink(0.45f) else HFColors.OnSurface,
            textDecoration = if (state == HFCauseOutcome.RULED_OUT)
                TextDecoration.LineThrough else null,
        )

        cause.check?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, color = HFColors.OnSurfaceMuted)
        }

        val manual = cause.manual_name
        val page = cause.page
        if (manual != null && page != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(HFColors.StatusCyan.copy(alpha = 0.12f))
                    .clickable(enabled = onOpenManual != null) { onOpenManual?.invoke(manual, page) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.MenuBook, null, tint = HFColors.StatusCyan,
                    modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(cause.citation ?: "p. $page", fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, color = HFColors.StatusCyan)
            }
        }

        cause.note?.takeIf { it.isNotBlank() }?.let {
            Text("Noted: $it", fontSize = 11.sp, color = HFColors.ink(0.60f))
        }

        if (state == HFCauseOutcome.UNTESTED && !closed) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                placeholder = { Text(stringResource(R.string.ts_what_did_you_find_optional), fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(HFColors.fill(0.10f))
                        .clickable(enabled = enabled) { onMark(HFCauseOutcome.RULED_OUT) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.ts_not_it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = HFColors.OnSurface) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(HFColors.StatusGreen)
                        .clickable(enabled = enabled) { onMark(HFCauseOutcome.CONFIRMED) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.ts_that_was_it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = HFColors.OnAccent) }
            }
        } else if (state != HFCauseOutcome.UNTESTED && !closed) {
            TextButton(onClick = { onMark(HFCauseOutcome.UNTESTED) }) {
                Text(stringResource(R.string.ts_undo), fontSize = 12.sp, color = HFColors.ink(0.55f))
            }
        }
    }
}

@Composable
private fun FoundPanel(hit: HFTroubleCause) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(HFColors.StatusGreen.copy(alpha = 0.09f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = HFColors.StatusGreen,
                modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ts_fault_found), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = HFColors.StatusGreen)
        }
        Text(hit.cause, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = HFColors.ink(0.85f))
        Text(
            stringResource(R.string.ts_the_repair_itself_is_a_procedure_open_the_wo),
            fontSize = 12.sp, color = HFColors.ink(0.60f),
        )
    }
}

@Composable
private fun SummaryPanel(summary: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(HFColors.StatusBlue.copy(alpha = 0.08f)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(stringResource(R.string.ts_for_the_logbook_and_the_next_mechanic), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, color = HFColors.ink(0.45f))
        Text(summary, fontSize = 12.sp, color = HFColors.ink(0.85f))
    }
}

@Composable
private fun StopBlock(
    stopping: Boolean,
    reason: String,
    busy: Boolean,
    onReason: (String) -> Unit,
    onBegin: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    if (!stopping) {
        TextButton(onClick = onBegin) {
            Text(stringResource(R.string.ts_stop_without_finding_it), fontSize = 12.sp, color = HFColors.ink(0.55f))
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(HFColors.fill(0.04f)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(stringResource(R.string.ts_why_are_you_stopping), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = HFColors.ink(0.60f))
        OutlinedTextField(
            value = reason,
            onValueChange = onReason,
            placeholder = {
                Text(stringResource(R.string.ts_out_of_time_waiting_on_a_part_needs_the_oem), fontSize = 12.sp)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(HFColors.StatusOrange)
                    .clickable(enabled = !busy) { onStop() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.ts_stop_and_record), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = HFColors.OnAccent) }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.ts_cancel), color = HFColors.ink(0.60f))
            }
        }
    }
}
