package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Tech-submitted request to fix a time entry the admin has rejected
 * (or any entry the tech notices is wrong). Mirrors the iOS/macOS/Desktop
 * struct so Supabase rows round-trip cleanly.
 *
 * The tech NEVER mutates the time entry itself — they write a note
 * here; admin reads it, edits the entry on their behalf, then marks
 * the request `applied`.
 */
@Serializable
data class HFTimeEntryCorrection(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("time_entry_id") val timeEntryId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String = "",
    @SerialName("requested_change") val requestedChange: String = "",
    /** "pending" | "applied" | "dismissed" */
    val status: String = "pending",
    @SerialName("decided_by_user_id") val decidedByUserId: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
