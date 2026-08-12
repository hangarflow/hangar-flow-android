package com.hangarflow.app.perf

import android.content.Context
import android.os.Build
import android.util.Log
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Catches crashes and reports them to `hf_perf_events` with `kind = "crash"`.
 *
 * Crash-time work writes to **disk only**. Uploading from an uncaught-exception
 * handler is the obvious design and the wrong one: the process is already dying,
 * the coroutine machinery may be the thing that broke, and a network round-trip
 * needs seconds the runtime will not give us. A file write is fast, synchronous,
 * and needs nothing but the filesystem. The report is sent on the next launch,
 * when there is a healthy process to send it from.
 *
 * The previously installed handler is always invoked afterwards, so the platform
 * still gets to record and terminate normally. Swallowing that would remove the
 * OS-level crash logs — trading one blind spot for another.
 */
object HFCrashReporter {

    private const val TAG = "HFCrash"
    private const val DIR = "hf-crashes"
    /** Enough to identify a fault; short enough not to bloat the table. */
    private const val MAX_TRACE_CHARS = 4000
    /** A crash loop must not upload hundreds of near-identical rows. */
    private const val MAX_PENDING = 5

    @Serializable
    private data class PendingCrash(
        val occurredAt: String,
        val type: String,
        val message: String,
        val trace: String,
        val screen: String,
        val thread: String,
        val deviceModel: String,
        val appVersion: String
    )

    @Serializable
    private data class CrashRow(
        val org_id: String,
        val user_id: String?,
        val actor_name: String,
        val kind: String = "crash",
        val screen: String,
        val action: String,
        val duration_ms: Int = 0,
        val platform: String = "android",
        val device_model: String,
        val app_version: String,
        val occurred_at: String,
        val detail: String
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var dir: File
    private var appVersion: String = "?"

    fun install(context: Context, appVersion: String) {
        this.appVersion = appVersion
        dir = File(context.filesDir, DIR).apply { mkdirs() }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { persist(thread, throwable) }
            // Always hand back to the platform handler so Android still logs
            // and terminates as it normally would.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Write the crash to disk. Must not allocate much or touch the network. */
    private fun persist(thread: Thread, t: Throwable) {
        if ((dir.listFiles()?.size ?: 0) >= MAX_PENDING) return

        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val crash = PendingCrash(
            occurredAt = Instant.now().toString(),
            type = t::class.java.simpleName,
            message = (t.message ?: "").take(300),
            trace = sw.toString().take(MAX_TRACE_CHARS),
            screen = runCatching { HFPerfMonitor.currentScreen }.getOrDefault("?"),
            thread = thread.name,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
            appVersion = appVersion
        )
        File(dir, "${System.currentTimeMillis()}.json").writeText(json.encodeToString(crash))
        Log.e(TAG, "crash persisted: ${crash.type}: ${crash.message}")
    }

    /**
     * Upload anything pending. Call once the user is signed in and an org is
     * known — a crash row without an org cannot be shown to anyone.
     *
     * Files are deleted only after a successful insert, so a failed upload is
     * retried next launch rather than lost.
     */
    fun uploadPending(scope: CoroutineScope) {
        if (!::dir.isInitialized) return
        val files = dir.listFiles()?.sortedBy { it.name } ?: return
        if (files.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            val orgId = SharedStore.currentOrgId ?: return@launch
            val me = SharedStore.state.value.currentUser
            for (f in files) {
                val crash = runCatching { json.decodeFromString<PendingCrash>(f.readText()) }
                    .getOrNull() ?: run { f.delete(); continue }   // unreadable: drop it

                val row = CrashRow(
                    org_id = orgId,
                    user_id = me?.id,
                    actor_name = me?.displayName ?: "",
                    screen = crash.screen,
                    action = "${crash.type}: ${crash.message}".take(180),
                    device_model = crash.deviceModel,
                    app_version = crash.appVersion,
                    // The crash's own time, not now — otherwise every report
                    // looks like it happened at launch.
                    occurred_at = crash.occurredAt,
                    detail = "thread=${crash.thread}\n\n${crash.trace}"
                )
                val sent = runCatching {
                    SupabaseClientProvider.client.postgrest.from("hf_perf_events").insert(row)
                }.isSuccess
                if (sent) f.delete() else break   // offline: keep for next time
            }
        }
    }
}
