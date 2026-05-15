package com.hangarflow.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tiny JSON-on-disk cache for the last synced `ShopState`. Lets the
 * app show the hangar's state immediately on cold start and survive a
 * shop wifi drop. This is deliberately simple — one file per org, full
 * overwrite on each successful cloud pull.
 */
object OfflineCache {
    private const val DIR = "offline"
    private const val FILE_PREFIX = "shop_state_"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun fileFor(orgId: String): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
        return File(dir, "$FILE_PREFIX$orgId.json")
    }

    fun save(orgId: String, state: ShopState) {
        val f = fileFor(orgId) ?: return
        runCatching {
            f.writeText(json.encodeToString(ShopState.serializer(), state))
        }
    }

    fun load(orgId: String): ShopState? {
        val f = fileFor(orgId) ?: return null
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            json.decodeFromString(ShopState.serializer(), f.readText())
        }.getOrNull()
    }

    fun clear(orgId: String? = null) {
        val ctx = appContext ?: return
        val dir = File(ctx.filesDir, DIR)
        if (!dir.exists()) return
        if (orgId == null) {
            dir.listFiles()?.forEach { it.delete() }
        } else {
            fileFor(orgId)?.delete()
        }
    }
}
