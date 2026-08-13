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
    /** Clock-in / clock-out for this segment (08:00-12:00). Null on rows
     *  filed before segment tracking; those carry only a duration. */
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    /** Derived server-side from the span whenever both ends are set, so
     *  the clock and the duration cannot disagree. */
    @SerialName("minutes_worked") val minutesWorked: Int = 0,
    val notes: String = "",
    /** Defaults to pending, NOT approved. Time has to be reviewed before
     *  anyone is paid on it; defaulting to approved silently pushed every
     *  entry straight onto the payroll. */
    @SerialName("approval_status") val approvalStatus: String = "pending",
    @SerialName("decided_by_user_id") val decidedByUserId: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    /** Rate stamped at approval so a later raise cannot re-price work
     *  that was already approved. */
    @SerialName("pay_rate_applied") val payRateApplied: Double? = null,
    @SerialName("created_at") val createdAt: String? = null
)
