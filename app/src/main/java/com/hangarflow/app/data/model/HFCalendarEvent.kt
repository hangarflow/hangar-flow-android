package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-created calendar event row from `hf_calendar_events`.
 * Surfaced on the Schedule calendar alongside plane arrivals,
 * RTS deadlines, and time-off requests. Mirrors the desktop/iOS
 * HFCalendarEvent so all clients round-trip the same rows.
 *
 * Events can be org-wide (planeId == null) or scoped to a specific
 * plane. start_date / end_date are inclusive date-only (YYYY-MM-DD);
 * single-day events use start == end.
 *
 * `visibility`:
 *   "public"     — everyone in the org sees it (default)
 *   "admin_only" — only admins see it
 *   "personal"   — only the creator sees it
 * The filter is applied in the calendar render, not at SQL.
 */
@Serializable
data class HFCalendarEvent(
    val id: String,
    @SerialName("org_id") val orgId: String,
    val title: String,
    val description: String = "",
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("event_kind") val eventKind: String = "general",
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    @SerialName("created_by_user_name") val createdByUserName: String = "",
    val visibility: String = "public",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
