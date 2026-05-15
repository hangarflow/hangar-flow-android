package com.hangarflow.app.data.cloud

/**
 * Same Supabase project the iOS + macOS apps talk to. The anon key is safe
 * to ship in the APK — every real write is gated by Row-Level Security.
 *
 * Values mirror iOS `SupabaseConfig.swift`. Keep them in sync if the project
 * ever gets re-keyed.
 */
object SupabaseConfig {
    const val URL = "https://ijlbpbdadbtlvovnazsd.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlqbGJwYmRhZGJ0bHZvdm5henNkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI3MTA2MDQsImV4cCI6MjA4ODI4NjYwNH0.xvWIJJktzG44LJIiYI0uFhArzVIs_RJtb-rUALZvoqo"
}
