package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One tech-submitted reimbursement (out-of-pocket business expense
 * with a receipt photo). Mirrors the iOS HFReimbursement model so
 * both clients round-trip the same Supabase rows without surprise
 * column drift. `amount_cents` is integer cents — never floats.
 *
 * Receipt photos live in the `reimbursement-receipts` storage bucket
 * and auto-delete 30 days after creation via the nightly pg_cron job.
 * After that the path here still resolves to a row, but the signed
 * URL returns 404 — that's expected and the admin should pull reports
 * inside the 30-day window.
 */
@Serializable
data class HFReimbursement(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String = "",
    @SerialName("amount_cents") val amountCents: Int,
    val description: String = "",
    @SerialName("receipt_storage_path") val receiptStoragePath: String? = null,
    @SerialName("time_entry_id") val timeEntryId: String? = null,
    val status: String = "pending",
    @SerialName("decided_by_user_id") val decidedByUserId: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
