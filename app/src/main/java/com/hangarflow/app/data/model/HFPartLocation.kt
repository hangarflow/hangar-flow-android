package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A shared hangar inventory row. Any tech in the org can create, edit,
 * or delete these; everyone sees the same list in realtime.
 *
 * `stockStatus` is a free string that the UI maps to known chips
 * (`ok` / `low` / `urgent` / `order_more`). `planeIds` links the part
 * to specific aircraft it's kept for — empty means "shop-wide stock".
 */
@Serializable
data class HFPartLocation(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("part_name") val partName: String = "",
    @SerialName("part_number") val partNumber: String = "",
    @SerialName("serial_number") val serialNumber: String = "",
    val location: String = "",
    val quantity: Int = 1,
    @SerialName("stock_status") val stockStatus: String = "ok",
    @SerialName("plane_ids") val planeIds: List<String> = emptyList(),
    val notes: String = "",
    @SerialName("updated_by_user_id") val updatedByUserId: String? = null,
    @SerialName("updated_by_user_name") val updatedByUserName: String = "",
    @SerialName("vendor_name") val vendorName: String? = null,
    @SerialName("vendor_website") val vendorWebsite: String? = null,
    @SerialName("vendor_phone") val vendorPhone: String? = null,
    @SerialName("min_stock_qty") val minStockQty: Int = 0,
    @SerialName("needs_urgent_reorder") val needsUrgentReorder: Boolean = false,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("plane_model") val planeModel: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    val status: String = "In Stock",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
