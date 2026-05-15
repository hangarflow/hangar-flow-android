package com.hangarflow.app.data

import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.cloud.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

/**
 * User-triggered issue/feedback reporter. Posts to the `report-issue` Edge
 * Function which emails hangarflow@gmail.com via Resend.
 *
 * Mirrors the Desktop implementation and the Swift version, just with the
 * Android-side AuthManager / SharedStore shapes.
 */
object IssueReporter {

    @Serializable
    private data class Payload(
        val subject: String,
        val body: String,
        val platform: String,
        val app_version: String,
        val os_name: String,
        val os_version: String,
        val org_id: String?,
        val org_name: String?,
        val user_email: String?,
        val user_name: String?,
        val user_role: String?
    )

    private const val APP_VERSION = "1.0.0"
    private const val TIMEOUT_MS = 10_000

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun submit(subject: String, body: String): Result = withContext(Dispatchers.IO) {
        if (subject.isBlank() || body.isBlank()) {
            return@withContext Result.Error("Subject and description are required.")
        }

        val auth = AuthManager.state.value
        val me = SharedStore.state.value.currentUser
        val payload = Payload(
            subject = subject.trim(),
            body = body.trim(),
            platform = "Android",
            app_version = APP_VERSION,
            os_name = "Android",
            os_version = android.os.Build.VERSION.RELEASE ?: "?",
            org_id = auth.orgId,
            org_name = auth.orgName,
            user_email = me?.email?.ifBlank { null },
            user_name = me?.displayName?.ifBlank { null },
            user_role = auth.role
        )

        runCatching {
            val url = URI("${SupabaseConfig.URL}/functions/v1/report-issue").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            conn.outputStream.use {
                it.write(Json.encodeToString(Payload.serializer(), payload).toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                Result.Success
            } else {
                val errText = conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "HTTP $code"
                conn.disconnect()
                Result.Error("Send failed: $errText")
            }
        }.getOrElse { Result.Error(it.message ?: "Network error") }
    }
}
