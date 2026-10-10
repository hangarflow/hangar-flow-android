package com.hangarflow.app.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher

/**
 * Single process-wide Supabase client — mirrors iOS `SupabaseClientProvider`.
 * All auth / Postgrest / Realtime / Storage access flows through this
 * instance so auth tokens are shared and realtime connections aren't
 * duplicated across feature screens.
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY
        ) {
            // Let the snapshot pull actually run in parallel.
            //
            // SharedStore.pullSnapshot fans out 22 fetches with `async`, and
            // it looked parallel in the code and was not. OkHttp's Dispatcher
            // caps concurrent calls to ONE HOST at 5 by default, and every
            // fetch goes to the same Supabase host — so 22 requests ran in
            // five waves no matter what the coroutines did.
            //
            // Measured on real devices over 30 days: Android pull_snapshot
            // p95 9,316ms against iOS's 1,596ms for identical work. iOS does
            // not hit this because URLSession multiplexes over HTTP/2 and has
            // no dispatcher-level per-host queue.
            //
            // 32 is comfortably above the 22 the pull issues, so the wave
            // behaviour disappears without letting a runaway loop open an
            // unbounded number of sockets. HTTP/2 carries them as streams on
            // one connection, so this costs sockets only if the server
            // negotiates HTTP/1.1.
            httpEngine = OkHttp.create {
                config {
                    dispatcher(
                        Dispatcher().apply {
                            maxRequests = 64
                            maxRequestsPerHost = 32
                        }
                    )
                }
            }
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}
