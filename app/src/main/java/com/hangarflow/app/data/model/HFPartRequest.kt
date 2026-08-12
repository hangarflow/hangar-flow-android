package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HFPartRequest(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("squawk_id") val squawkId: String? = null,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    val title: String,
    @SerialName("requested_part") val requestedPart: String,
    val urgency: String = "normal",
    val status: String = "requested",
    val notes: String = "",
    val quantity: Int = 1,
    /** "vendor" — the shop orders it from an outside distributor
     *  (Textron, Boeing, Spruce). "company" — requested from the
     *  operator who supplies it in house (Boutique, FedEx).
     *
     *  Read-only on Android for now: this client can only patch status,
     *  so it never writes these back and can't clobber them. */
    @SerialName("order_source") val orderSource: String = "vendor",
    @SerialName("supplier_name") val supplierName: String = "",
    @SerialName("requested_by") val requestedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
