package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A work order that is currently taking work — id and tail, nothing more.
 *
 * This client does not have a Work Orders screen and is not getting one. It needs
 * exactly one thing from that table: *which job is this aeroplane in for*, so a
 * squawk raised on the hangar floor lands on the thing that bills it. Without it,
 * every squawk a tech reported on a phone or tablet stayed loose, and an invoice
 * raised against the job never offered it.
 *
 * **Carries NO money, deliberately.** `labor_rate_cents`, `parts_markup_pct` and
 * `authorized_amount_cents` were physically dropped from `hf_work_orders` in
 * September because that row is readable by every member of the org and RLS
 * filters rows, not columns; the money lives in `hf_work_order_billing`, which is
 * admin-only on every verb. Nothing here should ever grow a money field — a tech
 * holds this object.
 */
@Serializable
data class HFOpenJob(
    val id: String,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("wo_number") val woNumber: Int? = null,
    val title: String = "",
    /** open | in_progress — the only two the fetch asks for. */
    val status: String = "open"
) {
    /** What everyone says out loud: "pull up WO 1042". */
    val label: String get() = woNumber?.let { "WO $it" } ?: "WO —"

    companion object {
        /**
         * The ONE job this aeroplane is in the shop for, or null.
         *
         * A pure function so the rule can be tested without a store — the rule is
         * the safety property, and it was previously a one-liner nothing checked.
         *
         * **Null when TWO jobs are open on the same tail**, deliberately, rather
         * than picking the newest. Auto-attach acts on this, and a wrong guess
         * bills one customer's squawk to another customer's job, silently, in a
         * place nobody looks again. Ambiguity means the work stays loose and a
         * human chooses it on a desktop. Identical rule on all four clients.
         */
        fun soleOpenJob(jobs: List<HFOpenJob>, planeId: String?): HFOpenJob? {
            if (planeId.isNullOrBlank()) return null
            return jobs.filter { it.planeId == planeId }.singleOrNull()
        }
    }
}
