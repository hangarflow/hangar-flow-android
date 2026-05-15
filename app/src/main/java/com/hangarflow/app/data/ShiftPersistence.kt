package com.hangarflow.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Persists the active shift to a tiny JSON file so the clock-in timer
 * survives app exits, process kills, and device reboots. Reads back on
 * cold start so the user's hours are never lost.
 */
object ShiftPersistence {
    private const val FILE_NAME = "active_shift.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun file(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, FILE_NAME)
    }

    @Serializable
    private data class ShiftSnapshot(
        val userId: String?,
        val userName: String,
        val startedAtEpochMs: Long,
        val lunchStartedAtEpochMs: Long?,
        val lunchMinutesAccrued: Int,
        val lunchTaken: Boolean
    )

    fun save(shift: ActiveShift?) {
        val f = file() ?: return
        if (shift == null) {
            f.delete()
            return
        }
        runCatching {
            val snap = ShiftSnapshot(
                userId = shift.userId,
                userName = shift.userName,
                startedAtEpochMs = shift.startedAt.toEpochMilli(),
                lunchStartedAtEpochMs = shift.lunchStartedAt?.toEpochMilli(),
                lunchMinutesAccrued = shift.lunchMinutesAccrued,
                lunchTaken = shift.lunchTaken
            )
            f.writeText(json.encodeToString(ShiftSnapshot.serializer(), snap))
        }
    }

    fun load(): ActiveShift? {
        val f = file() ?: return null
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            val snap = json.decodeFromString(ShiftSnapshot.serializer(), f.readText())
            ActiveShift(
                userId = snap.userId,
                userName = snap.userName,
                startedAt = Instant.ofEpochMilli(snap.startedAtEpochMs),
                lunchStartedAt = snap.lunchStartedAtEpochMs?.let { Instant.ofEpochMilli(it) },
                lunchMinutesAccrued = snap.lunchMinutesAccrued,
                lunchTaken = snap.lunchTaken
            )
        }.getOrNull()
    }

    fun clear() {
        file()?.delete()
    }
}
