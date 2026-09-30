package com.hangarflow.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's light/dark setting.
 *
 * Stored in the same `hf_prefs` file as the language, under the same key the
 * iOS and macOS clients use (`appAppearance`), so the three platforms describe
 * the setting identically — useful when a shop has a tech on a phone and an
 * admin on a Mac reading the same support answer.
 *
 * Unlike the language, changing this does NOT need an Activity restart:
 * `HFColors` reads its palette out of a snapshot state, so a swap recomposes
 * the tree in place.
 */
object HFAppearance {

    private const val PREFS = "hf_prefs"
    private const val KEY = "appAppearance"

    enum class Mode(val tag: String) {
        /** Follow the device's light/dark setting. */
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark"),
    }

    /**
     * Read once at startup into a snapshot state so the composition can react.
     * Dark is the default, not System: this app is used in hangars and on
     * shared shop devices, and dark is what every screenshot, every manual and
     * every tech already knows it to look like. Someone who wants to follow
     * the device can choose System.
     */
    var mode by mutableStateOf(Mode.DARK)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode = Mode.entries.firstOrNull { it.tag == tag } ?: Mode.DARK
        loaded = true
    }

    fun save(context: Context, newMode: Mode) {
        mode = newMode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, newMode.tag).apply()
    }

    /** The tag the server expects when it writes prose or notifications for us. */
    fun tag(): String = mode.tag
}
