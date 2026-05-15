package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A work log row — a maintenance task tied to a plane. Matches `hf_work_logs`.
 * Columns we don't yet render on Android are still modeled so the snapshot
 * round-trips cleanly through the shared store.
 */
@Serializable
data class HFWorkLog(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("plane_id") val planeId: String? = null,
    @SerialName("plane_tail_number") val planeTailNumber: String = "",
    val title: String,
    val category: String = "general",
    val status: String = "open",
    val details: String = "",
    @SerialName("reference_id") val referenceId: String? = null,
    @SerialName("reference_paragraph") val referenceParagraph: String? = null,
    @SerialName("reference_code") val referenceCode: String? = null,
    @SerialName("reference_title") val referenceTitle: String? = null,
    @SerialName("manual_effectivity") val manualEffectivity: String? = null,
    @SerialName("manual_page_start") val manualPageStart: Int? = null,
    @SerialName("manual_page_end") val manualPageEnd: Int? = null,
    @SerialName("manual_source_name") val manualSourceName: String? = null,
    @SerialName("exact_excerpt_only") val exactExcerptOnly: Boolean = false,
    @SerialName("source_item_number") val sourceItemNumber: String? = null,
    @SerialName("source_ata_code") val sourceAtaCode: String? = null,
    @SerialName("import_source_name") val importSourceName: String? = null,
    @SerialName("is_imported_record") val isImportedRecord: Boolean = false,
    @SerialName("linked_task_id") val linkedTaskId: String? = null,
    @SerialName("assigned_user_id") val assignedUserId: String? = null,
    @SerialName("assigned_user_name") val assignedUserName: String? = null,
    @SerialName("logged_minutes") val loggedMinutes: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
