package com.hangarflow.app.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's display language.
 *
 * Our own preference is the single source of truth, applied in
 * `MainActivity.attachBaseContext`. The alternative — Android 13's
 * per-app `LocaleManager` — only exists from API 33 and this app supports
 * API 26, so using it would mean two mechanisms and two places the
 * answer could disagree. One preference, applied on every Activity
 * creation, behaves identically on every supported version.
 *
 * Changing the language calls `Activity.recreate()`: Compose reads strings
 * through the Activity's resources, so the running process has to rebuild
 * its configuration for the new catalog to take effect.
 */
object HFLocale {

    private const val PREFS = "hf_prefs"
    private const val KEY = "appLanguageTag"

    /**
     * Languages with a complete translated catalog. Adding one means adding
     * `res/values-<tag>/strings.xml` AND an entry here — a locale listed
     * without a catalog silently falls back to English, which looks like a
     * bug rather than a missing translation.
     */
    enum class Language(val tag: String, val englishName: String, val nativeName: String) {
        SYSTEM("",   "System default", "System default"),
        ENGLISH("en", "English",    "English"),
        SPANISH("es", "Spanish",    "Español"),
        PORTUGUESE("pt", "Portuguese", "Português"),
        FRENCH("fr", "French",     "Français"),
        GERMAN("de", "German",     "Deutsch"),
        ITALIAN("it", "Italian",    "Italiano"),
        CHINESE("zh", "Chinese",    "中文"),
    }

    fun saved(context: Context): Language {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return Language.entries.firstOrNull { it.tag == tag && it != Language.SYSTEM }
            ?: Language.SYSTEM
    }

    fun save(context: Context, language: Language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language.tag).apply()
    }

    /**
     * Wraps a base context so its resources resolve against the chosen
     * language. SYSTEM returns the context untouched, which keeps the
     * device setting authoritative instead of pinning English.
     */
    fun wrap(base: Context): Context {
        val lang = saved(base)
        if (lang == Language.SYSTEM) return base
        val locale = Locale.forLanguageTag(lang.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
