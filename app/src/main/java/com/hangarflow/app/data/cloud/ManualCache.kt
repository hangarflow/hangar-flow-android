package com.hangarflow.app.data.cloud

import android.content.Context
import com.hangarflow.app.data.model.HFManual
import io.github.jan.supabase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local on-device cache for manual PDFs. Files live under
 * `filesDir/manuals/<manualId>.pdf`.
 *
 * Manuals are stored in Cloudflare R2, not Supabase Storage. iOS proxies
 * through the `mint-manual-url` Edge Function which validates org
 * membership and returns a short-lived presigned URL — Android does the
 * same so RLS rules stay enforced server-side.
 */
object ManualCache {
    private const val SUBDIR = "manuals"

    @Serializable
    private data class MintRequest(
        val mode: String,
        val storage_path: String,
        val content_type: String? = null
    )

    @Serializable
    private data class MintResponse(
        val url: String,
        val method: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("storage_bucket") val storageBucket: String
    )

    private const val TAG = "HFManualCache"

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            // Big manuals (Pilatus AMM is 159 MB) need long timeouts;
            // OkHttp defaults to 10s per request which fails everything
            // bigger than a few MB on slow shop networks.
            engine {
                config {
                    followRedirects(true)
                    connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    callTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // unbounded total
                }
            }
        }
    }

    fun localFileFor(context: Context, manual: HFManual): File {
        val dir = File(context.filesDir, SUBDIR).apply { mkdirs() }
        return File(dir, "${manual.id}.pdf")
    }

    fun isCached(context: Context, manual: HFManual): Boolean {
        val f = localFileFor(context, manual)
        return f.exists() && f.length() > 0
    }

    /**
     * Mint a download URL via the Edge Function, then stream the bytes
     * to disk. Existing file is overwritten. Surfaces every failure
     * mode with a clear message instead of swallowing or hanging.
     */
    suspend fun download(context: Context, manual: HFManual): File = withContext(Dispatchers.IO) {
        val storagePath = manual.storagePath?.takeIf { it.isNotBlank() }
            ?: error("Manual is missing storagePath — re-import on the desktop.")
        android.util.Log.i(TAG, "download: minting URL for storagePath=$storagePath")
        val signedUrl = try {
            mintUrl(storagePath, "download")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "download: mintUrl failed", t)
            throw IllegalStateException("Could not get a download URL: ${t.message}", t)
        }
        android.util.Log.i(TAG, "download: streaming from R2 → ${manual.fileName}")
        val outFile = localFileFor(context, manual)
        try {
            // prepareGet + execute keeps the body as a live stream — using
            // get() triggers Ktor's response-replay buffering which loads
            // the entire response into a single byte array (OOM on big
            // manuals like the 159 MB Pilatus AMM).
            httpClient.prepareGet(signedUrl).execute { response ->
                android.util.Log.i(TAG, "download: R2 status=${response.status.value}")
                if (response.status.value !in 200..299) {
                    val body = runCatching { response.bodyAsText() }.getOrDefault("")
                    android.util.Log.e(TAG, "download: non-2xx ${response.status.value}: ${body.take(200)}")
                    throw IllegalStateException(
                        if (response.status.value == 404)
                            "This manual's file is missing from cloud storage. Re-import the PDF to restore it."
                        else "Couldn't download this manual (${response.status.value}). Please try again."
                    )
                }
                outFile.outputStream().use { sink ->
                    response.bodyAsChannel().copyTo(sink)
                }
                android.util.Log.i(TAG, "download: wrote ${outFile.length()} bytes to ${outFile.name}")
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "download: stream failed", t)
            outFile.delete()
            throw t
        }
        outFile
    }

    /**
     * Mint a short-lived signed URL for a manual PDF. Used when we want
     * to hand the URL to Android's system PDF viewer for streaming.
     */
    suspend fun signedManualURL(manual: HFManual): String {
        val storagePath = manual.storagePath?.takeIf { it.isNotBlank() }
            ?: error("Manual is missing storagePath — re-import on the desktop.")
        return mintUrl(storagePath, "download")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun mintUrl(storagePath: String, mode: String): String {
        val client = SupabaseClientProvider.client
        val httpResponse = client.functions.invoke(
            function = "mint-manual-url",
            body = MintRequest(mode = mode, storage_path = storagePath)
        )
        val raw = httpResponse.bodyAsText()
        val parsed = runCatching { json.decodeFromString<MintResponse>(raw) }.getOrNull()
            ?: error("Manual URL service returned an unexpected response.")
        return parsed.url
    }
}
