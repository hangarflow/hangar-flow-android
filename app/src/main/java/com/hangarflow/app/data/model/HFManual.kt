package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HFManual(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String? = null,
    val title: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("source_type") val sourceType: String = "manualPDF",
    @SerialName("storage_bucket") val storageBucket: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    @SerialName("revision_label") val revisionLabel: String? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
