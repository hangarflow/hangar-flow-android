package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HFPartRequest(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("squawk_id") val squawkId: String? = null,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    val title: String,
    @SerialName("requested_part") val requestedPart: String,
    val urgency: String = "normal",
    val status: String = "requested",
    val notes: String = "",
    @SerialName("requested_by") val requestedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
