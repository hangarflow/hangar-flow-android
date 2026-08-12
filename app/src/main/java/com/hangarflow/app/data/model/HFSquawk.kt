package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HFSquawk(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = "general",
    val status: String = "open",
    @SerialName("reported_by_user_id") val reportedByUserId: String? = null,
    @SerialName("reported_by_user_name") val reportedByUserName: String? = null,
    @SerialName("assigned_user_name") val assignedUserName: String? = null,
    @SerialName("photo_paths") val photoPaths: List<String> = emptyList(),
    // Corrective action — what was actually done to fix the squawk, plus
    // who closed it and when. Fills in as the squawk resolves; editable.
    @SerialName("corrective_action") val correctiveAction: String = "",
    @SerialName("corrected_by_user_id") val correctedByUserId: String? = null,
    @SerialName("corrected_by_user_name") val correctedByUserName: String = "",
    @SerialName("corrected_at") val correctedAt: String? = null,
    /** Work log created alongside this squawk (Apple pairs them at
     *  report time). The server keeps the two statuses in step, so
     *  resolving one resolves the other. This client doesn't create
     *  pairs, but it carries the field so a round-trip can't drop it. */
    @SerialName("linked_work_log_id") val linkedWorkLogId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
