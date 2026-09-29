package com.hangarflow.app

import android.app.Application
import com.hangarflow.app.i18n.HFStrings

/**
 * Exists so string lookup is available before any Activity, Service or
 * Receiver runs.
 *
 * The FCM service and the content provider declared in the manifest can start
 * without MainActivity ever being created — a push arriving on a cold start is
 * the obvious case. Initialising in MainActivity would leave those paths
 * falling back to untranslated text, which is the kind of gap that only shows
 * up in production and only for non-English users.
 */
class HangarFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HFStrings.init(this)
    }
}
