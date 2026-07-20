package com.hangarflow.app.perf

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Live performance watchdog. Turns "the app feels glitchy" into concrete
 * data. Two signals:
 *   - JankStats reports dropped / slow frames (visual stutter) → [recordJank].
 *   - [markTapped] + [markShown] and [trace] time key interactions (opening a
 *     sheet, the sign-in pull); the slow ones become "slow_action" events.
 *
 * The worst offenders are reported — throttled, capped per session — to the
 * `hf_perf_events` table so slow spots surface across every device, not just
 * when someone happens to be watching.
 */
object HFPerfMonitor {
    private const val TAG = "HFPerf"
    private const val JANK_MS = 250L          // a single frame this slow = a real hiccup
    private const val SLOW_ACTION_MS = 600L   // tap → visible result this slow = noticeable
    private const val MAX_REPORTS_PER_SESSION = 60
    private const val THROTTLE_MS = 4000L      // min gap between reports of the same (kind|screen|action)

    /** The screen/interaction currently on top — set at nav points so jank is attributable. */
    @Volatile var currentScreen: String = "home"
    @Volatile var appVersion: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<PerfEvent>(capacity = 128)
    private var started = false
    private var reportCount = 0
    private val lastReport = HashMap<String, Long>()
    @Volatile private var pendingTap: Pair<String, Long>? = null

    @Serializable
    private data class PerfRow(
        val org_id: String,
        val user_id: String?,
        val actor_name: String,
        val kind: String,
        val screen: String,
        val action: String,
        val duration_ms: Int,
        val platform: String = "android",
        val device_model: String,
        val app_version: String,
    )

    private data class PerfEvent(val kind: String, val screen: String, val action: String, val durationMs: Long)

    fun start(appVersion: String) {
        this.appVersion = appVersion
        if (started) return
        started = true
        scope.launch {
            for (e in queue) runCatching { report(e) }
        }
    }

    /** JankStats callback: a frame took too long to draw. */
    fun recordJank(durationMs: Long, screen: String = currentScreen) {
        if (durationMs < JANK_MS) return
        Log.w(TAG, "jank ${durationMs}ms on $screen")
        enqueue(PerfEvent("jank", screen, "", durationMs))
    }

    /** Mark the instant a user tapped something that should open a screen/sheet. */
    fun markTapped(action: String) { pendingTap = action to now() }

    /** Call when the expected screen/sheet first composes; records the tap→shown delay if slow. */
    fun markShown(action: String) {
        val p = pendingTap ?: return
        if (p.first != action) return
        pendingTap = null
        val ms = now() - p.second
        if (ms >= SLOW_ACTION_MS) {
            Log.w(TAG, "slow open '$action': ${ms}ms")
            enqueue(PerfEvent("slow_action", currentScreen, action, ms))
        }
    }

    /** Time a suspend operation (e.g. a data pull); slow ones become slow_action events. */
    suspend fun <T> trace(action: String, block: suspend () -> T): T {
        val t = now()
        try {
            return block()
        } finally {
            val ms = now() - t
            if (ms >= SLOW_ACTION_MS) {
                Log.w(TAG, "slow '$action': ${ms}ms")
                enqueue(PerfEvent("slow_action", currentScreen, action, ms))
            }
        }
    }

    private fun enqueue(e: PerfEvent) {
        if (reportCount >= MAX_REPORTS_PER_SESSION) return
        val key = "${e.kind}|${e.screen}|${e.action}"
        val n = now()
        if (n - (lastReport[key] ?: 0L) < THROTTLE_MS) return
        lastReport[key] = n
        reportCount++
        queue.trySend(e)
    }

    private suspend fun report(e: PerfEvent) {
        val orgId = SharedStore.currentOrgId ?: return
        val me = SharedStore.state.value.currentUser
        val row = PerfRow(
            org_id = orgId,
            user_id = me?.id,
            actor_name = me?.displayName ?: "",
            kind = e.kind,
            screen = e.screen,
            action = e.action,
            duration_ms = e.durationMs.toInt(),
            device_model = "${Build.MANUFACTURER} ${Build.MODEL}",
            app_version = appVersion,
        )
        runCatching { SupabaseClientProvider.client.postgrest.from("hf_perf_events").insert(row) }
    }

    private fun now() = SystemClock.elapsedRealtime()
}
