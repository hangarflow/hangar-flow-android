package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class HFTimeOffStatus { pending, approved, denied }

/**
 * One pending/approved/denied PTO row in `hf_time_off_requests`.
 * Mirrors the iOS HFTimeOffRequest model so both clients can read
 * and round-trip the same Supabase rows without column mismatch.
 *
 * `start_date` / `end_date` are date-only (YYYY-MM-DD) — they map
 * to Postgres `date` columns, not timestamps.
 */
@Serializable
data class HFTimeOffRequest(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val reason: String = "",
    val status: String = "pending",
    @SerialName("decided_by_user_id") val decidedByUserId: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
