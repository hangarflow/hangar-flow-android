package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What one employee earns and how often they're paid.
 *
 * Its own table rather than a column on the user profile: every org
 * member can read profiles, so a rate stored there would publish
 * everyone's wage to everyone. RLS gives admins the whole payroll and a
 * tech only their own row — so on a tech's device this list holds at
 * most one entry.
 */
@Serializable
data class HFEmployeePay(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("hourly_rate") val hourlyRate: Double = 0.0,
    /** weekly | biweekly | monthly — set per employee, not per org. */
    @SerialName("pay_schedule") val paySchedule: String = "biweekly",
    @SerialName("pay_anchor_date") val payAnchorDate: String? = null,
    val notes: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
