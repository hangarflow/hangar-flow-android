package com.hangarflow.app.push

import android.content.Context
import android.util.Log
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Registers the device's FCM token with the Supabase `hf_user_devices`
 * table so the backend can target push notifications at specific users.
 *
 * Called from two places:
 *   1. `HFMessagingService.onNewToken` — Firebase token rotated.
 *   2. `MainActivity.onCreate` — ensure registration on every cold start
 *      (covers the case where the token was already issued but the
 *      previous upsert failed due to network).
 */
object FcmTokenManager {
    private const val TAG = "FcmToken"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Try to fetch the current FCM token and register it. Safe to call
     * even if Firebase isn't configured — catches everything.
     */
    fun ensureRegistered(context: Context) {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    registerToken(context, token)
                }
                .addOnFailureListener {
                    Log.w(TAG, "Couldn't fetch FCM token (Firebase not configured?)")
                }
        } catch (t: Throwable) {
            // FirebaseApp not initialized — google-services.json missing.
            Log.w(TAG, "Firebase not initialized: ${t.message}")
        }
    }

    @kotlinx.serialization.Serializable
    private data class DeviceRow(
        val device_id: String,
        val user_id: String?,
        val platform: String,
        val fcm_token: String
    )

    fun registerToken(context: Context, token: String) {
        val deviceId = SharedStore.deviceIdentifier()
        val userId = SharedStore.state.value.currentUser?.id
        scope.launch {
            runCatching {
                val client = SupabaseClientProvider.client
                client.postgrest.from("hf_user_devices").upsert(
                    DeviceRow(
                        device_id = deviceId,
                        user_id = userId,
                        platform = "android",
                        fcm_token = token
                    )
                )
                Log.i(TAG, "Token registered for device ${deviceId.take(8)}")
            }.onFailure {
                Log.w(TAG, "Token upsert failed: ${it.message}")
            }
        }
    }
}
