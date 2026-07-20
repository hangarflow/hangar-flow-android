package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A piece of shop equipment — "due lists for the shop's gear."
 *
 * Tugs, compressors, GPUs, forklifts, jacks, lifts, shop trucks, and
 * calibrated tools (torque wrenches, gauges, test sets) all live here.
 * Org-wide: every tech in the org reads AND writes these rows, same as
 * [HFPartLocation]. Maintenance/calibration due-items live in
 * [HFEquipmentMaintenanceItem]; each service is logged in
 * [HFEquipmentServiceEntry].
 */
@Serializable
data class HFEquipment(
    val id: String,
    @SerialName("org_id") val orgId: String,
    val name: String = "",
    @SerialName("equipment_type") val equipmentType: String = "general",
    val location: String = "",
    val manufacturer: String = "",
    @SerialName("model_number") val modelNumber: String = "",
    @SerialName("serial_number") val serialNumber: String = "",
    val status: String = "active",
    @SerialName("photo_paths") val photoPaths: List<String> = emptyList(),
    @SerialName("doc_paths") val docPaths: List<HFEquipmentDoc> = emptyList(),
    @SerialName("assigned_owner_id") val assignedOwnerId: String? = null,
    @SerialName("assigned_owner_name") val assignedOwnerName: String = "",
    @SerialName("usage_hours") val usageHours: Double = 0.0,
    @SerialName("warranty_expires_at") val warrantyExpiresAt: String? = null,
    @SerialName("registration_expires_at") val registrationExpiresAt: String? = null,
    val notes: String = "",
    @SerialName("updated_by_user_id") val updatedByUserId: String? = null,
    @SerialName("updated_by_user_name") val updatedByUserName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** An attached document (cal cert, receipt, manual) in the private
 *  `equipment-docs` bucket. Rendered via a short-lived signed URL. */
@Serializable
data class HFEquipmentDoc(
    val path: String = "",
    val bucket: String = "equipment-docs",
    val name: String = "",
    val kind: String = "doc"   // doc | cal_cert | receipt | photo
)

/**
 * A single maintenance/calibration item on a piece of equipment — the
 * per-equipment "due list." Interval is either TIME-based ([intervalMonths])
 * or USAGE-based ([intervalHours]). [itemKind] "calibration" flags the
 * FAA-required cal tracking for tools.
 */
@Serializable
data class HFEquipmentMaintenanceItem(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("equipment_id") val equipmentId: String,
    val title: String = "",
    @SerialName("item_kind") val itemKind: String = "maintenance",   // maintenance | calibration
    @SerialName("interval_type") val intervalType: String = "time",  // time | usage
    @SerialName("interval_months") val intervalMonths: Int? = null,
    @SerialName("interval_hours") val intervalHours: Double? = null,
    @SerialName("last_done_at") val lastDoneAt: String? = null,
    @SerialName("last_done_hours") val lastDoneHours: Double? = null,
    @SerialName("next_due_at") val nextDueAt: String? = null,
    @SerialName("next_due_hours") val nextDueHours: Double? = null,
    @SerialName("remind_user_id") val remindUserId: String? = null,
    val notes: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** One logged service/repair/calibration event. */
@Serializable
data class HFEquipmentServiceEntry(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("equipment_id") val equipmentId: String,
    @SerialName("maintenance_item_id") val maintenanceItemId: String? = null,
    @SerialName("performed_at") val performedAt: String = "",
    @SerialName("performed_by_user_id") val performedByUserId: String? = null,
    @SerialName("performed_by_user_name") val performedByUserName: String = "",
    @SerialName("hours_at_service") val hoursAtService: Double? = null,
    val notes: String = "",
    @SerialName("doc_paths") val docPaths: List<HFEquipmentDoc> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null
)

// ---------------------------------------------------------------------------
// Due-status computation — the same green/amber/red language as squawk cards.
// Computed client-side so it stays live as the clock ticks / usage changes.
// ---------------------------------------------------------------------------

enum class EquipmentDueSeverity { OK, DUE_SOON, OVERDUE, UNKNOWN }

data class EquipmentDueInfo(val severity: EquipmentDueSeverity, val label: String)

private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun parseDueDate(raw: String?): LocalDate? =
    raw?.takeIf { it.length >= 10 }?.let {
        runCatching { LocalDate.parse(it.take(10), isoDate) }.getOrNull()
    }

fun HFEquipmentMaintenanceItem.dueInfo(currentUsageHours: Double): EquipmentDueInfo {
    if (intervalType == "usage") {
        val due = nextDueHours ?: return EquipmentDueInfo(EquipmentDueSeverity.UNKNOWN, "No due set")
        val remaining = due - currentUsageHours
        return when {
            remaining < 0 -> EquipmentDueInfo(EquipmentDueSeverity.OVERDUE, "OVD ${fmtHrs(-remaining)}h")
            remaining <= 10 -> EquipmentDueInfo(EquipmentDueSeverity.DUE_SOON, "${fmtHrs(remaining)}h left")
            else -> EquipmentDueInfo(EquipmentDueSeverity.OK, "${fmtHrs(remaining)}h left")
        }
    } else {
        val due = parseDueDate(nextDueAt) ?: return EquipmentDueInfo(EquipmentDueSeverity.UNKNOWN, "No due set")
        val days = ChronoUnit.DAYS.between(LocalDate.now(), due).toInt()
        return when {
            days < 0 -> EquipmentDueInfo(EquipmentDueSeverity.OVERDUE, "OVD ${-days}d")
            days == 0 -> EquipmentDueInfo(EquipmentDueSeverity.OVERDUE, "Due today")
            days <= 30 -> EquipmentDueInfo(EquipmentDueSeverity.DUE_SOON, "${days}d left")
            else -> EquipmentDueInfo(EquipmentDueSeverity.OK, "${days}d left")
        }
    }
}

fun worstDueSeverity(items: List<HFEquipmentMaintenanceItem>, currentUsageHours: Double): EquipmentDueSeverity {
    if (items.isEmpty()) return EquipmentDueSeverity.UNKNOWN
    val severities = items.map { it.dueInfo(currentUsageHours).severity }
    return when {
        severities.any { it == EquipmentDueSeverity.OVERDUE } -> EquipmentDueSeverity.OVERDUE
        severities.any { it == EquipmentDueSeverity.DUE_SOON } -> EquipmentDueSeverity.DUE_SOON
        severities.any { it == EquipmentDueSeverity.OK } -> EquipmentDueSeverity.OK
        else -> EquipmentDueSeverity.UNKNOWN
    }
}

private fun fmtHrs(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
