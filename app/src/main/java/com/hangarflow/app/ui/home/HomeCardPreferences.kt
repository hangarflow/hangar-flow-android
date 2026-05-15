package com.hangarflow.app.ui.home

import android.content.Context

/**
 * Persists the user's preferred order for the home card grid. Stored
 * as a single comma-separated string of card IDs in SharedPreferences.
 * Long-press-and-swap on the home updates this immediately.
 */
object HomeCardPreferences {
    private const val PREFS_NAME = "hf_home_cards"
    private const val KEY_ORDER = "card_order_v2"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrder(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ORDER, null) ?: return emptyList()
        return raw.split(',').filter { it.isNotBlank() }
    }

    fun saveOrder(context: Context, ids: List<String>) {
        prefs(context).edit().putString(KEY_ORDER, ids.joinToString(",")).apply()
    }

    /**
     * Apply the saved order to the role-default card list. Any cards
     * not present in the saved order (e.g. a new card type added in a
     * later release) are appended at the end so they don't disappear.
     */
    fun applyOrder(defaults: List<String>, saved: List<String>): List<String> {
        if (saved.isEmpty()) return defaults
        val savedFiltered = saved.filter { it in defaults }
        val missing = defaults.filter { it !in savedFiltered }
        return savedFiltered + missing
    }
}
