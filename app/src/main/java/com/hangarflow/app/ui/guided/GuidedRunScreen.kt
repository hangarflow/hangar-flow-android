package com.hangarflow.app.ui.guided

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.cloud.GuidedRunCloud
import com.hangarflow.app.data.cloud.HFGuidedRun
import com.hangarflow.app.data.cloud.HFProcedureStep
import com.hangarflow.app.data.cloud.HFRoger
import com.hangarflow.app.data.cloud.HFRogerIntent
import com.hangarflow.app.data.cloud.HFRogerVoice
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.compose.ui.res.stringResource
import com.hangarflow.app.R

/**
 * The walkthrough on a tablet, which is the device this feature was imagined
 * for: propped against a toolbox, aeroplane in front of you, hands dirty.
 *
 * So it sits between the two other clients deliberately. Windows lists the
 * whole procedure because a desk has room; iOS shows one card because a phone
 * does not. A 10-inch tablet at arm's length wants the current step LARGE and
 * the others legible but out of the way — and, unlike either, it wants to be
 * driven without touching the screen at all.
 *
 * Roger is the reason. Push-to-talk rather than a wake word: a hangar is loud,
 * a wake word fires on compressor cycles and dropped tools, and holding a
 * button makes it obvious when the microphone is live.
 */
@Composable
fun GuidedRunScreen(
    run: HFGuidedRun,
    onOpenManual: ((manual: String, page: Int) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var live by remember(run.id) { mutableStateOf(run) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var rogerSaid by remember { mutableStateOf<String?>(null) }

    val voice = remember { HFRogerVoice(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    var micGranted by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) voice.startListening()
        else note = "Roger needs the microphone. You can still tap the steps."
    }

    fun stepText(index: Int): String {
        val s = live.steps.getOrNull(index) ?: return ""
        return "Step ${s.n ?: (index + 1)}. ${s.what}"
    }

    fun setDone(number: Int, done: Boolean) {
        scope.launch {
            busy = true
            val msg = withContext(Dispatchers.IO) {
                GuidedRunCloud.setStepDone(live, number, done, Instant.now().toString())
            }
            busy = false
            note = msg
            // Only move once the database agreed. A tick that appears and
            // silently did not save is the failure this cannot afford.
            if (msg == null) {
                val next = live.doneSteps.toMutableSet()
                    .apply { if (done) add(number) else remove(number) }
                live = live.copy(doneSteps = next)
            }
        }
    }

    // What Roger does with what it heard. Everything here is answered WITHOUT
    // a model except an actual question — see HFRoger.intent.
    voice.onHeard = { said ->
        when (val intent = HFRoger.intent(said)) {
            is HFRogerIntent.Next -> {
                val i = live.currentIndex
                if (i != null) {
                    val n = live.steps[i].n ?: (i + 1)
                    setDone(n, true)
                    val following = live.steps.getOrNull(i + 1)
                    voice.say(
                        if (following == null) "That was the last step."
                        else "Step ${following.n ?: (i + 2)}. ${following.what}"
                    )
                } else voice.say("Everything is ticked off.")
            }
            is HFRogerIntent.Back -> {
                val i = live.currentIndex ?: live.steps.size
                val prev = (i - 1).coerceAtLeast(0)
                live.steps.getOrNull(prev)?.let {
                    setDone(it.n ?: (prev + 1), false)
                    voice.say(stepText(prev))
                }
            }
            is HFRogerIntent.RepeatStep ->
                voice.say(stepText(live.currentIndex ?: 0))
            is HFRogerIntent.ReadStep ->
                voice.say(
                    live.steps.indexOfFirst { it.n == intent.n }
                        .takeIf { it >= 0 }?.let { stepText(it) }
                        ?: "There's no step ${intent.n}."
                )
            is HFRogerIntent.WhereIsIt -> {
                val s = live.steps.getOrNull(live.currentIndex ?: 0)
                voice.say(
                    if (s?.manual_name != null && s.page != null)
                        "${s.manual_name}, page ${s.page}."
                    else "That step doesn't cite a page."
                )
            }
            is HFRogerIntent.HowLong -> voice.say(
                live.estimateMinutes?.let { "About $it minutes." }
                    ?: "No estimate for this one."
            )
            is HFRogerIntent.Tools -> voice.say(
                if (live.tools.isEmpty()) "No tools listed."
                else live.tools.joinToString(", ")
            )
            // The only branch that costs anything. Left to the caller to wire
            // to walkthrough-voice rather than guessed at locally — a made-up
            // answer about a torque figure is the one thing this must never do.
            is HFRogerIntent.Question ->
                rogerSaid = "Asking about: ${intent.said}"
            is HFRogerIntent.None -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(HFColors.Background)) {
        Header(live, onClose)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            note?.let {
                item {
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = HFColors.StatusOrange)
                }
            }
            rogerSaid?.let {
                item {
                    Text(it, fontSize = 12.sp, color = HFColors.StatusCyan)
                }
            }
            if (live.tools.isNotEmpty() || live.cautions.isNotEmpty() ||
                live.estimateMinutes != null
            ) {
                item { Briefing(live) }
            }
            itemsIndexed(live.steps) { index, step ->
                val number = step.n ?: (index + 1)
                StepCard(
                    step = step,
                    number = number,
                    done = number in live.doneSteps,
                    isCurrent = index == live.currentIndex,
                    enabled = !busy,
                    onOpenManual = onOpenManual,
                    onToggle = { setDone(number, it) },
                )
            }
            if (live.gaps.isNotEmpty()) item { GapsPanel(live.gaps) }
        }

        TalkButton(
            listening = voice.listening,
            unavailable = voice.unavailable,
            heard = voice.heard,
            onPress = {
                if (micGranted) voice.startListening()
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            },
            onRelease = { voice.stopListening() },
        )
    }
}

@Composable
private fun Header(run: HFGuidedRun, onClose: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    run.taskTitle.ifBlank { "Walkthrough" },
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HFColors.OnSurface,
                )
                Text(
                    "${run.doneSteps.size} of ${run.steps.size} done" +
                        (run.estimateMinutes?.let { "  ·  about $it min" } ?: ""),
                    fontSize = 12.sp, color = HFColors.OnSurfaceMuted,
                )
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.guided_close), color = HFColors.OnSurfaceMuted) }
        }
        LinearProgressIndicator(
            progress = { run.progress.toFloat() },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = HFColors.StatusCyan,
            trackColor = HFColors.fill(0.08f),
        )
    }
}

/**
 * Tools, time and cautions BEFORE the first step — the three things a mechanic
 * wants before walking to the aeroplane, not after discovering step 6 needs a
 * puller that is in someone's truck.
 */
@Composable
private fun Briefing(run: HFGuidedRun) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(HFColors.fill(0.05f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (run.tools.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Build, null, tint = HFColors.OnSurfaceMuted,
                    modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(run.tools.joinToString("  ·  "), fontSize = 13.sp,
                    color = HFColors.OnSurface)
            }
        }
        run.estimateMinutes?.let { mins ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, tint = HFColors.OnSurfaceMuted,
                    modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    // The sample size is shown because a median of three jobs
                    // is a different claim from a median of thirty.
                    "About $mins min" + (run.estimateSampleSize?.let {
                        " — this shop's median over $it job${if (it == 1) "" else "s"}"
                    } ?: ""),
                    fontSize = 12.sp, color = HFColors.OnSurfaceMuted,
                )
            }
        }
        run.cautions.forEach {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = HFColors.StatusOrange,
                    modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = HFColors.StatusOrange)
            }
        }
    }
}

@Composable
private fun StepCard(
    step: HFProcedureStep,
    number: Int,
    done: Boolean,
    isCurrent: Boolean,
    enabled: Boolean,
    onOpenManual: ((String, Int) -> Unit)?,
    onToggle: (Boolean) -> Unit,
) {
    val tint = when {
        done -> HFColors.StatusGreen
        isCurrent -> HFColors.StatusCyan
        else -> HFColors.ink(0.35f)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) HFColors.fill(0.08f) else HFColors.fill(0.03f))
            .border(
                1.dp,
                if (isCurrent) HFColors.StatusCyan.copy(alpha = 0.30f) else HFColors.fill(0.10f),
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (done) tint else HFColors.fill(0.12f))
                .clickable(enabled = enabled) { onToggle(!done) },
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Outlined.Check, "Done", tint = HFColors.OnAccent,
                modifier = Modifier.size(17.dp))
            else Text("$number", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = HFColors.OnSurfaceMuted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                step.what,
                // The current step is read at arm's length; the others only
                // need to be legible enough to see what is coming.
                fontSize = if (isCurrent) 18.sp else 14.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                color = if (done) HFColors.OnSurfaceMuted else HFColors.OnSurface,
            )
            val manual = step.manual_name
            val page = step.page
            if (manual != null && page != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(HFColors.StatusCyan.copy(alpha = 0.12f))
                        .clickable(enabled = onOpenManual != null) {
                            onOpenManual?.invoke(manual, page)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.MenuBook, null, tint = HFColors.StatusCyan,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(step.citation ?: "$manual p. $page", fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = HFColors.StatusCyan)
                }
            }
            if (isCurrent) {
                step.extract?.takeIf { it.isNotBlank() }?.let { extract ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .background(HFColors.fill(0.05f)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.guided_the_manual_s_own_words), fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, color = HFColors.ink(0.45f))
                        Text(extract, fontSize = 12.sp, color = HFColors.OnSurfaceMuted)
                        if (step.extract_truncated == true) {
                            // Said out loud rather than left to be discovered.
                            Text(
                                stringResource(R.string.guided_this_extract_is_cut_short_open_the_page_befo),
                                fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                color = HFColors.StatusOrange,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapsPanel(gaps: List<String>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(HFColors.StatusOrange.copy(alpha = 0.08f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(stringResource(R.string.guided_your_manuals_don_t_cover), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = HFColors.StatusOrange)
        gaps.forEach { Text("· $it", fontSize = 12.sp, color = HFColors.OnSurface) }
    }
}

/** Hold to talk. Big, bottom, thumb-reachable, and obvious when it is live. */
@Composable
private fun TalkButton(
    listening: Boolean,
    unavailable: String?,
    heard: String?,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        heard?.takeIf { listening.not() }?.let {
            Text("“$it”", fontSize = 12.sp, color = HFColors.ink(0.55f))
        }
        unavailable?.let {
            Text(it, fontSize = 12.sp, color = HFColors.StatusOrange)
        }
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
                .background(
                    if (listening) HFColors.StatusCyan
                    else HFColors.StatusCyan.copy(alpha = 0.14f)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Mic, null,
                    tint = if (listening) HFColors.OnAccent else HFColors.StatusCyan,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (listening) "Listening…" else "Hold to talk to Roger",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (listening) HFColors.OnAccent else HFColors.StatusCyan,
                )
            }
        }
    }
}
