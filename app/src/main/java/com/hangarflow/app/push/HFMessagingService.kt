package com.hangarflow.app.push

import android.util.Log
import androidx.core.app.NotificationCompat
import com.hangarflow.app.PushNotifications
import com.hangarflow.app.R

/**
 * FCM messaging service. Receives push messages from the Hangar Flow
 * backend and shows a local notification. Also handles token refresh —
 * new tokens are sent to `hf_user_devices` via [FcmTokenManager].
 *
 * This service compiles and registers even without `google-services.json`.
 * If Firebase isn't initialized (no config file), `onNewToken` simply
 * won't fire and the app falls back to local-only notifications.
 *
 * To activate:
 *   1. Create a Firebase project, add Android app `com.hangarflow.app`.
 *   2. Download `google-services.json` → drop into `app/`.
 *   3. Apply the `com.google.gms.google-services` plugin in `app/build.gradle.kts`.
 */
class HFMessagingService : com.google.firebase.messaging.FirebaseMessagingService() {
    companion object {
        private const val TAG = "HFMessaging"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed: ${token.take(12)}…")
        FcmTokenManager.registerToken(this, token)
    }

    override fun onMessageReceived(message: com.google.firebase.messaging.RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Hangar Flow"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        PushNotifications.ensureRegistered(this)
        val builder = NotificationCompat.Builder(this, PushNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.notify(message.messageId.hashCode(), builder.build())
    }
}
