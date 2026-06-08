package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HFTimeEntry(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_name") val userName: String = "",
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    @SerialName("linked_task_id") val linkedTaskId: String? = null,
    @SerialName("linked_work_log_id") val linkedWorkLogId: String? = null,
    @SerialName("linked_squawk_id") val linkedSquawkId: String? = null,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("minutes_worked") val minutesWorked: Int = 0,
    val notes: String = "",
    @SerialName("approval_status") val approvalStatus: String = "approved",
    @SerialName("decided_by_user_id") val decidedByUserId: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
