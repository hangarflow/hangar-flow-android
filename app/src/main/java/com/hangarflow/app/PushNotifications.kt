package com.hangarflow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Push-notification scaffolding.
 *
 * A notification channel is registered here so local notifications (and,
 * once Firebase Cloud Messaging is wired up, remote notifications) land
 * under a single "Hangar Flow alerts" channel the user can mute from
 * system settings.
 *
 * FCM integration is intentionally deferred until the shop has a
 * Firebase project:
 *   1. In Firebase Console → create project → add Android app using
 *      `com.hangarflow.app` package.
 *   2. Download `google-services.json` → drop into `app/` next to
 *      `build.gradle.kts`.
 *   3. Add plugin `com.google.gms.google-services` at the top of
 *      `app/build.gradle.kts` and bump the root `libs.versions.toml`
 *      with `com.google.firebase:firebase-messaging`.
 *   4. Add a `FirebaseMessagingService` subclass that forwards the
 *      device's FCM token to a new `hf_user_devices` table, and handles
 *      incoming messages via `NotificationManagerCompat.notify()` on
 *      this channel.
 */
object PushNotifications {
    const val CHANNEL_ID = "hf_alerts"
    private const val CHANNEL_NAME = "Hangar Flow alerts"
    private const val CHANNEL_DESC = "Squawks, work-log assignments, and part-request updates."

    fun ensureRegistered(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
        }
        mgr.createNotificationChannel(channel)
    }
}
