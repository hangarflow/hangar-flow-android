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
    /** Who logged this work (the "Added by" paper trail). */
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    @SerialName("created_by_user_name") val createdByUserName: String? = null,
    // AI-organize enrichment (written server-side by organize-worklogs).
    @SerialName("ai_recommended_parts") val aiRecommendedParts: List<HFAIRecommendedPart>? = null,
    @SerialName("ai_related_reference_ids") val aiRelatedReferenceIds: List<String>? = null,
    @SerialName("ai_enriched_at") val aiEnrichedAt: String? = null,
    /** Inspection-checklist sign-off state. One entry per checked-off
     *  manual-reference item. Persisted in the `checklist_state` JSONB
     *  column so every device sees the same progress. */
    @SerialName("checklist_state") val checklistState: List<HFChecklistEntry>? = null,
    /** When set, the log floats to the top of the Work Logs list.
     *  Pin → now() ISO timestamp; unpin → null. */
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** One sign-off line on an inspection checklist — which reference was
 *  checked, by whom, and when. `refId` keys back to the
 *  `hf_manual_references.id` the checklist row was built from. */
@Serializable
data class HFChecklistEntry(
    @SerialName("ref_id") val refId: String,
    val done: Boolean = false,
    val by: String = "",
    val initials: String = "",
    val at: String? = null
)

/** One advisory part the AI thinks a work log needs. partNumber is null
 *  when the AI couldn't confirm a real number (it never invents one). */
@Serializable
data class HFAIRecommendedPart(
    @SerialName("part_number") val partNumber: String? = null,
    val description: String = "",
    val reason: String = ""
)
