package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A part physically moving in or out of the building.
 *
 * Covers purchases arriving, cores going back, units sent out for overhaul,
 * and warranty returns — one log rather than four, because the question a
 * parts room actually asks is "what's coming, what's gone, and what's late".
 *
 * The core deposit is the shop's money, not the customer's: it never reaches
 * an invoice, and it is owed back by the VENDOR. Treat it as a receivable
 * with a deadline attached.
 *
 * Mirrors the Desktop model field for field — same table, same rules.
 */
@Serializable
data class HFPartMovement(
    val id: String,
    @SerialName("org_id") val orgId: String,
    /** "in" or "out". */
    val direction: String,
    /** purchase | core_return | outside_repair | warranty_return */
    val kind: String,
    @SerialName("part_number") val partNumber: String = "",
    val description: String = "",
    @SerialName("serial_number") val serialNumber: String? = null,
    val quantity: Int = 1,
    @SerialName("vendor_name") val vendorName: String = "",
    @SerialName("work_order_id") val workOrderId: String? = null,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    @SerialName("part_request_id") val partRequestId: String? = null,
    val carrier: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("shipped_at") val shippedAt: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("core_due_back_by") val coreDueBackBy: String? = null,
    @SerialName("core_deposit_cents") val coreDepositCents: Long? = null,
    @SerialName("core_refunded_cents") val coreRefundedCents: Long? = null,
    val condition: String? = null,
    /** shelf = stock was incremented; aircraft = fitted on arrival, never
     *  entered stock. Null until someone decides. */
    val disposition: String? = null,
    @SerialName("inventory_part_id") val inventoryPartId: String? = null,
    val status: String = "awaiting",
    val notes: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    @SerialName("created_by_user_name") val createdByUserName: String? = null
) {
    val isInbound: Boolean get() = direction == "in"
    val isCore: Boolean get() = kind == "core_return"

    /** Still owed back to the vendor — the deposit is at risk. */
    val coreOutstanding: Boolean
        get() = isCore && status in setOf("awaiting", "in_transit", "delivered")

    val dueDate: LocalDate?
        get() = coreDueBackBy?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Negative once the vendor's window has closed. */
    fun daysRemaining(today: LocalDate = LocalDate.now()): Long? =
        dueDate?.let { ChronoUnit.DAYS.between(today, it) }

    /** Past the deadline but not yet written off — the urgent bucket. */
    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean =
        coreOutstanding && (daysRemaining(today)?.let { it < 0 } ?: false)

    val kindLabel: String
        get() = when (kind) {
            "purchase" -> "Purchase"
            "core_return" -> "Core return"
            "outside_repair" -> "Out for overhaul"
            "warranty_return" -> "Warranty return"
            else -> kind
        }

    val statusLabel: String
        get() = when (status) {
            "awaiting" -> "Not shipped yet"
            "in_transit" -> "In transit"
            "received" -> "Received"
            "delivered" -> "Vendor has it"
            "refunded" -> "Deposit refunded"
            "rejected" -> "Core rejected"
            "forfeited" -> "Deposit forfeited"
            "closed" -> "Closed"
            else -> status
        }

    /** What the card's chip says. On a core that's a deadline, not a state
     *  name — the deadline is the thing someone has to act on. */
    fun chipLabel(today: LocalDate = LocalDate.now()): String {
        if (!coreOutstanding) return statusLabel
        val d = daysRemaining(today) ?: return "No deadline"
        return when {
            d < 0 -> "${-d}d past due"
            d == 0L -> "Due today"
            else -> "${d}d left"
        }
    }
}
