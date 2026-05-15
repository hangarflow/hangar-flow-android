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
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
