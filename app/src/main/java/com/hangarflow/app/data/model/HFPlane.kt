package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One shop aircraft. Port of iOS `HFPlane`. Field names use snake_case to
 * match the `hf_aircraft` Postgres columns directly so kotlinx.serialization
 * can decode Supabase responses without a bridging DTO.
 */
@Serializable
data class HFPlane(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("tail_number") val tailNumber: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("outline_hex") val outlineHex: String? = null,
    @SerialName("arrival_date") val arrivalDate: String? = null,
    @SerialName("deadline_date") val deadlineDate: String? = null,
    /** Receiving inspection: what the plane is coming in for (100-hr,
     *  200-hr, annual, 5-year, etc.). Tagged on arrival, shown on the
     *  schedule. Free text; null = not specified. */
    @SerialName("incoming_inspection") val incomingInspection: String? = null,
    /** Aircraft type/model (Phase 4) — manuals tagged with the same type are
     *  suggested for attach when the plane is added. */
    @SerialName("aircraft_type") val aircraftType: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
