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
    // ── Aircraft intake: Times & Cycles (optional, mechanic reference) ──
    // Captured at drop-off; pure reference (no tracking/computation). All
    // optional — a single-engine plane just leaves the rest null. Stored as
    // text so any format the shop already uses is accepted.
    @SerialName("airframe_hours") val airframeHours: String? = null,
    @SerialName("airframe_cycles") val airframeCycles: String? = null,
    @SerialName("hobbs") val hobbs: String? = null,
    @SerialName("tach") val tach: String? = null,
    @SerialName("engine1_hours") val engine1Hours: String? = null,
    @SerialName("engine1_cycles") val engine1Cycles: String? = null,
    @SerialName("engine2_hours") val engine2Hours: String? = null,
    @SerialName("engine2_cycles") val engine2Cycles: String? = null,
    @SerialName("engine3_hours") val engine3Hours: String? = null,
    @SerialName("engine3_cycles") val engine3Cycles: String? = null,
    @SerialName("prop1_hours") val prop1Hours: String? = null,
    @SerialName("prop2_hours") val prop2Hours: String? = null,
    @SerialName("apu_hours") val apuHours: String? = null,
    @SerialName("apu_cycles") val apuCycles: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
