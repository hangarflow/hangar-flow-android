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
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
