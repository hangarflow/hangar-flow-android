package com.hangarflow.app.i18n

import android.content.Context
import androidx.annotation.StringRes

/**
 * String lookup for code that runs OUTSIDE composition.
 *
 * `stringResource()` is a composable and cannot be called from a repository,
 * a data builder, a coroutine in SharedStore, or an FCM service — which is
 * where most of this app's error and status messages are produced. Those
 * messages still reach the user, so they still need translating.
 *
 * The important detail is WHICH context. `HFLocale.wrap()` is applied to the
 * Activity in `attachBaseContext`, so an Activity resolves the chosen
 * language correctly — but the *application* context never went through that
 * wrap. Calling `appContext.getString()` would quietly return the SYSTEM
 * language while every Activity string showed the chosen one, and the two
 * would disagree with no error to explain why. So this wraps explicitly.
 *
 * The wrapped context is cached and rebuilt when the saved language changes,
 * which is how a language switch reaches code that never recomposes.
 */
object HFStrings {

    @Volatile private var appContext: Context? = null
    @Volatile private var localized: Context? = null
    @Volatile private var builtForTag: String? = null

    /**
     * Called from `HangarFlowApp.onCreate`, before any Activity, Service or
     * Receiver can run — so a push notification arriving on a cold start
     * still gets translated text.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun resolve(): Context? {
        val app = appContext ?: return null
        val tag = HFLocale.saved(app).tag
        val cached = localized
        if (cached != null && builtForTag == tag) return cached
        val wrapped = HFLocale.wrap(app)
        localized = wrapped
        builtForTag = tag
        return wrapped
    }

    /**
     * Returns the English fallback rather than throwing when init has not run.
     * These are error messages: a crash inside the error path would replace a
     * useful message with no message at all, and it would happen precisely
     * when something else is already wrong.
     */
    fun get(@StringRes id: Int, fallback: String = ""): String {
        val ctx = resolve() ?: return fallback
        return runCatching { ctx.getString(id) }.getOrElse { fallback }
    }

    fun get(@StringRes id: Int, vararg args: Any, fallback: String = ""): String {
        val ctx = resolve() ?: return fallback
        return runCatching { ctx.getString(id, *args) }.getOrElse { fallback }
    }
}
