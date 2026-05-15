package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cross-platform Task. Mirrors iOS `HFTask`. Status values come from
 * `HFTaskStatus`: open / inProgress / waitingOnParts / done.
 *
 * Tasks are distinct from Work Logs: a task is something that needs doing
 * (often spawned from a squawk), while a work log is the recorded action
 * once the wrench actually turns. A task can be linked to a work log via
 * `linkedWorkLogId` and to the squawk that spawned it via `linkedSquawkId`.
 */
@Serializable
data class HFTask(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    val title: String,
    val details: String = "",
    val category: String = "general",
    val status: String = "open",
    @SerialName("assigned_user_id") val assignedUserId: String? = null,
    @SerialName("assigned_user_name") val assignedUserName: String? = null,
    @SerialName("linked_work_log_id") val linkedWorkLogId: String? = null,
    @SerialName("linked_squawk_id") val linkedSquawkId: String? = null,
    @SerialName("is_from_squawk") val isFromSquawk: Boolean = false,
    @SerialName("waiting_on_parts") val waitingOnParts: Boolean = false,
    @SerialName("logged_minutes") val loggedMinutes: Int = 0,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
