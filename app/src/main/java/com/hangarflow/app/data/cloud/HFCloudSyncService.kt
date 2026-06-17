package com.hangarflow.app.data.cloud

import com.hangarflow.app.data.model.HFManual
import com.hangarflow.app.data.model.HFPartLocation
import com.hangarflow.app.data.model.HFPartRequest
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.data.model.HFSquawk
import com.hangarflow.app.data.model.HFTask
import com.hangarflow.app.data.model.HFTimeEntry
import com.hangarflow.app.data.model.HFUserProfile
import com.hangarflow.app.data.model.HFWorkLog
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import kotlin.time.Duration.Companion.seconds
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Kotlin port of iOS `HFCloudSyncService`. Owns the Postgrest calls that
 * read and write shop data. Each screen observes the `SharedStore` rather
 * than calling this directly — this service is the pipe, not the source
 * of truth.
 */
class HFCloudSyncService {
    private val client get() = SupabaseClientProvider.client

    suspend fun fetchPlanes(orgId: String): List<HFPlane> =
        client.postgrest
            .from("hf_aircraft")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchWorkLogs(orgId: String): List<HFWorkLog> =
        client.postgrest
            .from("hf_work_logs")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchUserProfiles(orgId: String): List<HFUserProfile> =
        client.postgrest
            .from("hf_user_profiles")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchManuals(orgId: String): List<HFManual> =
        client.postgrest
            .from("hf_manuals")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchSquawks(orgId: String): List<HFSquawk> =
        client.postgrest
            .from("hf_squawks")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchTimeEntries(orgId: String): List<HFTimeEntry> =
        client.postgrest
            .from("hf_time_entries")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchPartRequests(orgId: String): List<HFPartRequest> =
        client.postgrest
            .from("hf_part_requests")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun fetchPartLocations(orgId: String): List<HFPartLocation> =
        client.postgrest
            .from("hf_part_locations")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    // ── Audit trail (paper trail) ──
    @kotlinx.serialization.Serializable
    private data class NewAuditRow(
        val org_id: String, val entity_type: String, val entity_id: String?,
        val action: String, val actor_user_id: String?, val actor_name: String, val summary: String
    )

    suspend fun insertAuditEvent(
        orgId: String, entityType: String, entityId: String?, action: String,
        actorUserId: String?, actorName: String, summary: String
    ) {
        client.postgrest.from("hf_audit_log").insert(
            NewAuditRow(orgId, entityType, entityId, action, actorUserId, actorName, summary)
        )
    }

    suspend fun fetchAuditLog(orgId: String, limit: Int = 300): List<com.hangarflow.app.data.model.HFAuditEvent> =
        client.postgrest
            .from("hf_audit_log")
            .select {
                filter { eq("org_id", orgId) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList()

    suspend fun fetchTasks(orgId: String): List<HFTask> =
        client.postgrest
            .from("hf_tasks")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun upsertTask(task: HFTask) {
        client.postgrest.from("hf_tasks").upsert(task)
    }

    suspend fun updateTaskStatus(taskId: String, status: String) {
        client.postgrest.from("hf_tasks").update(mapOf("status" to status)) {
            filter { eq("id", taskId) }
        }
    }

    suspend fun deleteTask(id: String) {
        client.postgrest.from("hf_tasks").delete {
            filter { eq("id", id) }
        }
    }

    @kotlinx.serialization.Serializable
    private data class PartLocationRow(
        val id: String? = null,
        val org_id: String? = null,
        val part_name: String,
        val part_number: String,
        val serial_number: String,
        val location: String,
        val quantity: Int,
        val stock_status: String,
        val plane_ids: List<String>,
        val notes: String,
        val updated_by_user_id: String?,
        val updated_by_user_name: String,
        val photo_paths: List<String>
    )

    suspend fun createPartLocation(
        orgId: String,
        partName: String,
        partNumber: String,
        serialNumber: String,
        location: String,
        quantity: Int,
        stockStatus: String,
        planeIds: List<String>,
        notes: String,
        updatedByUserId: String?,
        updatedByUserName: String,
        photoPaths: List<String> = emptyList()
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val row = PartLocationRow(
            id = id,
            org_id = orgId,
            part_name = partName.trim(),
            part_number = partNumber.trim(),
            serial_number = serialNumber.trim(),
            location = location.trim(),
            quantity = quantity.coerceAtLeast(0),
            stock_status = stockStatus.ifBlank { "ok" },
            plane_ids = planeIds,
            notes = notes.trim(),
            updated_by_user_id = updatedByUserId,
            updated_by_user_name = updatedByUserName.trim(),
            photo_paths = photoPaths
        )
        client.postgrest.from("hf_part_locations").insert(row)
        return id
    }

    suspend fun updatePartLocation(
        id: String,
        partName: String,
        partNumber: String,
        serialNumber: String,
        location: String,
        quantity: Int,
        stockStatus: String,
        planeIds: List<String>,
        notes: String,
        updatedByUserId: String?,
        updatedByUserName: String,
        photoPaths: List<String> = emptyList()
    ) {
        val row = PartLocationRow(
            part_name = partName.trim(),
            part_number = partNumber.trim(),
            serial_number = serialNumber.trim(),
            location = location.trim(),
            quantity = quantity.coerceAtLeast(0),
            stock_status = stockStatus.ifBlank { "ok" },
            plane_ids = planeIds,
            notes = notes.trim(),
            updated_by_user_id = updatedByUserId,
            updated_by_user_name = updatedByUserName.trim(),
            photo_paths = photoPaths
        )
        client.postgrest.from("hf_part_locations").update(row) {
            filter { eq("id", id) }
        }
    }

    suspend fun deletePartLocation(id: String) {
        client.postgrest.from("hf_part_locations").delete {
            filter { eq("id", id) }
        }
    }

    /** Patch only the quantity — used by the quick "Took one" stock pull. */
    suspend fun updatePartLocationQuantity(id: String, quantity: Int) {
        client.postgrest.from("hf_part_locations")
            .update(mapOf("quantity" to quantity.coerceAtLeast(0))) { filter { eq("id", id) } }
    }

    /** Flip a part request through its lifecycle: requested → ordered → received → installed. */
    suspend fun updatePartRequestStatus(id: String, status: String) {
        client.postgrest.from("hf_part_requests").update(mapOf("status" to status)) {
            filter { eq("id", id) }
        }
    }

    suspend fun updateSquawkStatus(id: String, status: String) {
        client.postgrest.from("hf_squawks").update(mapOf("status" to status)) {
            filter { eq("id", id) }
        }
    }

    @kotlinx.serialization.Serializable
    private data class NewPartRequestRow(
        val id: String,
        val org_id: String,
        val squawk_id: String?,
        val plane_id: String?,
        val plane_tail_number: String?,
        val title: String,
        val requested_part: String,
        val urgency: String,
        val status: String,
        val notes: String,
        val requested_by: String?,
        val quantity: Int,
        val need_by_date: String?,
        val assigned_tech_user_id: String?,
        val manual_reference_id: String?
    )

    /**
     * Insert a part request row, optionally linked to an owning squawk
     * or a manual-reference search hit. Mirrors the Desktop / iOS paths
     * so parts created from any platform land in the same queue with
     * full metadata.
     */
    suspend fun createPartRequest(
        orgId: String,
        squawkId: String?,
        planeId: String?,
        planeTailNumber: String?,
        title: String,
        requestedPart: String,
        urgency: String,
        requestedBy: String?,
        quantity: Int = 1,
        needByDate: String? = null,
        assignedTechUserId: String? = null,
        manualReferenceId: String? = null,
        notes: String = ""
    ) {
        val row = NewPartRequestRow(
            id = java.util.UUID.randomUUID().toString(),
            org_id = orgId,
            squawk_id = squawkId,
            plane_id = planeId,
            plane_tail_number = planeTailNumber,
            title = title.ifBlank { requestedPart },
            requested_part = requestedPart.ifBlank { title },
            urgency = urgency,
            status = "requested",
            notes = notes,
            requested_by = requestedBy,
            quantity = quantity.coerceAtLeast(1),
            need_by_date = needByDate,
            assigned_tech_user_id = assignedTechUserId,
            manual_reference_id = manualReferenceId
        )
        client.postgrest.from("hf_part_requests").insert(row)
    }

    @kotlinx.serialization.Serializable
    data class ManualSearchHit(
        val id: String,
        @kotlinx.serialization.SerialName("manual_id") val manualId: String? = null,
        @kotlinx.serialization.SerialName("plane_tail_number") val planeTailNumber: String? = null,
        val title: String? = null,
        @kotlinx.serialization.SerialName("reference_code") val referenceCode: String? = null,
        @kotlinx.serialization.SerialName("body_text") val bodyText: String? = null,
        @kotlinx.serialization.SerialName("page_start") val pageStart: Int? = null,
        @kotlinx.serialization.SerialName("page_end") val pageEnd: Int? = null,
        @kotlinx.serialization.SerialName("source_manual_name") val sourceManualName: String? = null
    )

    /**
     * Simple ILIKE search over manual reference bodies — matches the
     * iOS "Find Parts" fallback. Returns up to 30 hits for the org.
     */
    /** Fetch every reference row for a specific manual — used to build
     *  the in-app bookmark list (grouped by ATA chapter). Capped at 2000
     *  so massive AMMs don't blow out memory. */
    suspend fun fetchReferencesForManual(
        orgId: String,
        manualId: String
    ): List<ManualSearchHit> =
        client.postgrest
            .from("hf_manual_references")
            .select(io.github.jan.supabase.postgrest.query.Columns.list(
                "id", "manual_id", "plane_tail_number", "title",
                "reference_code", "body_text", "page_start", "page_end",
                "source_manual_name"
            )) {
                filter {
                    eq("org_id", orgId)
                    eq("manual_id", manualId)
                }
                limit(2000)
            }
            .decodeList()

    /** Pull every manual-reference row tagged with the given
     *  inspection_kind (e.g. "200hr") scoped to the plane's attached
     *  manuals. Powers the inspection-checklist panel on a work-log. */
    suspend fun fetchInspectionChecklistRefs(
        orgId: String,
        manualIds: List<String>,
        inspectionKind: String
    ): List<ManualSearchHit> {
        if (manualIds.isEmpty()) return emptyList()
        val cols = io.github.jan.supabase.postgrest.query.Columns.list(
            "id", "manual_id", "plane_tail_number", "title",
            "reference_code", "body_text", "page_start", "page_end",
            "source_manual_name"
        )
        return client.postgrest
            .from("hf_manual_references")
            .select(cols) {
                filter {
                    eq("org_id", orgId)
                    eq("inspection_kind", inspectionKind)
                    isIn("manual_id", manualIds)
                }
                order(column = "page_start", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING, nullsFirst = false)
                limit(500)
            }
            .decodeList()
    }

    /** Persist the checklist_state JSONB on a work-log. The blob is
     *  shape `[{ "ref_id": "...", "done": true, "by": "...", "at": "..." }]`. */
    suspend fun updateWorkLogChecklistState(workLogId: String, stateJson: String) {
        client.postgrest.from("hf_work_logs")
            .update(
                mapOf("checklist_state" to kotlinx.serialization.json.Json.parseToJsonElement(stateJson))
            ) {
                filter { eq("id", workLogId) }
            }
    }

    suspend fun searchManualReferences(
        orgId: String,
        query: String,
        restrictToPlaneTail: String? = null
    ): List<ManualSearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val tail = restrictToPlaneTail?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val cols = io.github.jan.supabase.postgrest.query.Columns.list(
            "id", "manual_id", "plane_tail_number", "title",
            "reference_code", "body_text", "page_start", "page_end",
            "source_manual_name"
        )

        // Real maintenance sections (category='section') come first —
        // these are the procedure pages techs actually want. We use
        // Postgres FTS via the precomputed `search_vector` so word
        // boundaries + stemming are respected: "tire" matches "tires"
        // but not "entire" or "Time Limited Inspection".
        val sections = client.postgrest
            .from("hf_manual_references")
            .select(cols) {
                filter {
                    eq("org_id", orgId)
                    eq("category", "section")
                    if (tail != null) eq("plane_tail_number", tail)
                    textSearch(
                        column = "search_vector",
                        query = trimmed,
                        textSearchType = io.github.jan.supabase.postgrest.query.filter.TextSearchType.WEBSEARCH,
                        config = "english"
                    )
                }
                limit(20)
            }
            .decodeList<ManualSearchHit>()

        val remaining = (30 - sections.size).coerceAtLeast(0)
        if (remaining == 0) return sections

        val pages = client.postgrest
            .from("hf_manual_references")
            .select(cols) {
                filter {
                    eq("org_id", orgId)
                    neq("category", "section")
                    if (tail != null) eq("plane_tail_number", tail)
                    textSearch(
                        column = "search_vector",
                        query = trimmed,
                        textSearchType = io.github.jan.supabase.postgrest.query.filter.TextSearchType.WEBSEARCH,
                        config = "english"
                    )
                }
                limit(remaining.toLong())
            }
            .decodeList<ManualSearchHit>()

        return sections + pages
    }

    /**
     * Mint a short-lived signed URL for a squawk photo stored in the
     * `squawk-photos` bucket. Used by both the list thumbnails and the
     * detail sheet — the bucket is private, so every view goes through
     * a signed URL re-requested on demand.
     */
    suspend fun signedSquawkPhotoURL(path: String, expiresInSeconds: Int = 3600): String {
        return client.storage
            .from("squawk-photos")
            .createSignedUrl(path, expiresIn = expiresInSeconds.seconds)
    }

    /**
     * Upload a single JPEG photo under the RLS-prefixed path
     * `<orgId>/<squawkId>/<uuid>.jpg`. Returns the stored path so the
     * caller can attach it to `hf_squawks.photo_paths`.
     */
    suspend fun uploadSquawkPhoto(
        data: ByteArray,
        orgId: String,
        squawkId: String
    ): String {
        val path = "$orgId/$squawkId/${java.util.UUID.randomUUID()}.jpg"
        client.storage.from("squawk-photos").upload(path, data) {
            contentType = io.ktor.http.ContentType.Image.JPEG
            upsert = false
        }
        return path
    }

    /**
     * Mint a short-lived signed URL for a part-location photo stored in
     * the `part-location-photos` bucket. Mirrors `signedSquawkPhotoURL` —
     * the bucket is private, so every thumbnail / full-screen view goes
     * through a signed URL re-requested on demand.
     */
    suspend fun signedPartLocationPhotoURL(path: String, expiresInSeconds: Int = 3600): String {
        return client.storage
            .from("part-location-photos")
            .createSignedUrl(path, expiresIn = expiresInSeconds.seconds)
    }

    /**
     * Upload a single JPEG photo under the RLS-prefixed path
     * `<orgId>/<partLocationId>/<uuid>.jpg`. Returns the stored path so
     * the caller can attach it to `hf_part_locations.photo_paths`.
     */
    suspend fun uploadPartLocationPhoto(
        data: ByteArray,
        orgId: String,
        partLocationId: String
    ): String {
        val path = "$orgId/$partLocationId/${java.util.UUID.randomUUID()}.jpg"
        client.storage.from("part-location-photos").upload(path, data) {
            contentType = io.ktor.http.ContentType.Image.JPEG
            upsert = false
        }
        return path
    }

    /**
     * Remove a part-location photo from the `part-location-photos` bucket.
     * Used by the Replace / Remove flow so we don't orphan old objects
     * (one photo per part). Best-effort — caller wraps in runCatching.
     */
    suspend fun deletePartLocationPhoto(path: String) {
        client.storage.from("part-location-photos").delete(path)
    }

    /**
     * Upload a single receipt JPEG to the reimbursement-receipts bucket.
     * Path: `{orgId}/{userId}/{YYYY-MM}/{reimbursementId}.jpg` so the
     * admin scrolling the bucket sees a clean per-user-per-month tree.
     * Caller is responsible for converting HEIC → JPEG before upload.
     */
    suspend fun uploadReceiptPhoto(
        jpeg: ByteArray,
        orgId: String,
        userAuthId: String,
        reimbursementId: String,
        capturedAtIso: String? = null
    ): String {
        val month = (capturedAtIso ?: java.time.Instant.now().toString())
            .let { it.substring(0, kotlin.math.min(7, it.length)) } // YYYY-MM
        val path = "$orgId/$userAuthId/$month/$reimbursementId.jpg"
        client.storage.from("reimbursement-receipts").upload(path, jpeg) {
            contentType = io.ktor.http.ContentType.Image.JPEG
            upsert = false
        }
        return path
    }

    @kotlinx.serialization.Serializable
    private data class NewReimbursementRow(
        val id: String,
        val org_id: String,
        val user_id: String,
        val user_name: String,
        val amount_cents: Int,
        val description: String,
        val receipt_storage_path: String?,
        val time_entry_id: String?,
        val status: String
    )

    /**
     * Insert a tech-submitted reimbursement. Status starts at `pending`;
     * an admin reviews via the desktop CSV export and updates status
     * with `updateReimbursementStatus`.
     */
    suspend fun createReimbursement(
        id: String,
        orgId: String,
        userId: String,
        userName: String,
        amountCents: Int,
        description: String,
        receiptStoragePath: String?,
        timeEntryId: String?,
        sourceDevice: String
    ) {
        client.postgrest.from("hf_reimbursements").insert(
            NewReimbursementRow(
                id = id,
                org_id = orgId,
                user_id = userId,
                user_name = userName,
                amount_cents = amountCents,
                description = description,
                receipt_storage_path = receiptStoragePath,
                time_entry_id = timeEntryId,
                status = "pending"
            )
        )
        emitOrgEvent(orgId = orgId, sourceDevice = sourceDevice, eventType = "reimbursement_submitted")
    }

    /** Pulls every reimbursement row for the org so the Hours / Payroll
     *  list can show pending + decided entries side-by-side. */
    suspend fun fetchReimbursements(orgId: String): List<com.hangarflow.app.data.model.HFReimbursement> =
        client.postgrest.from("hf_reimbursements")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    /** Admin approve/deny of a reimbursement. */
    suspend fun updateReimbursementStatus(
        reimbursementId: String, status: String, decidedByUserId: String, decidedByName: String
    ) {
        @kotlinx.serialization.Serializable
        data class StatusPatch(
            val status: String,
            @kotlinx.serialization.SerialName("decided_by_user_id") val decidedByUserId: String,
            @kotlinx.serialization.SerialName("decided_by_name") val decidedByName: String,
            @kotlinx.serialization.SerialName("decided_at") val decidedAt: String,
            @kotlinx.serialization.SerialName("updated_at") val updatedAt: String
        )
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        client.postgrest.from("hf_reimbursements")
            .update(StatusPatch(status, decidedByUserId, decidedByName, now, now)) {
                filter { eq("id", reimbursementId) }
            }
    }

    // ── Time-entry corrections ──

    /** Org-wide time-entry-correction queue. Pending + decided rows
     *  come back together; the admin queue UI filters by status. */
    suspend fun fetchTimeEntryCorrections(
        orgId: String
    ): List<com.hangarflow.app.data.model.HFTimeEntryCorrection> =
        client.postgrest.from("hf_time_entry_corrections")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun upsertTimeEntryCorrection(
        correction: com.hangarflow.app.data.model.HFTimeEntryCorrection
    ) {
        client.postgrest.from("hf_time_entry_corrections").upsert(correction)
    }

    suspend fun updateTimeEntryCorrectionStatus(
        correctionId: String,
        status: String,
        decidedByUserId: String,
        decidedByName: String
    ) {
        @kotlinx.serialization.Serializable
        data class StatusPatch(
            val status: String,
            @kotlinx.serialization.SerialName("decided_by_user_id") val decidedByUserId: String,
            @kotlinx.serialization.SerialName("decided_by_name") val decidedByName: String,
            @kotlinx.serialization.SerialName("decided_at") val decidedAt: String,
            @kotlinx.serialization.SerialName("updated_at") val updatedAt: String
        )
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        client.postgrest.from("hf_time_entry_corrections")
            .update(StatusPatch(status, decidedByUserId, decidedByName, now, now)) {
                filter { eq("id", correctionId) }
            }
    }

    /** Targeted UPDATE for admin time-off approve/deny (direct update keyed
     *  by id — upsert would trip the requester-only INSERT RLS policy). */
    suspend fun decideTimeOffRequestRow(
        requestId: String, status: String,
        decidedByUserId: String?, decidedByName: String?, decidedAt: String
    ) {
        @kotlinx.serialization.Serializable
        data class DecisionPatch(
            val status: String,
            @kotlinx.serialization.SerialName("decided_by_user_id") val decidedByUserId: String?,
            @kotlinx.serialization.SerialName("decided_by_name") val decidedByName: String?,
            @kotlinx.serialization.SerialName("decided_at") val decidedAt: String,
            @kotlinx.serialization.SerialName("updated_at") val updatedAt: String
        )
        client.postgrest.from("hf_time_off_requests")
            .update(DecisionPatch(status, decidedByUserId, decidedByName, decidedAt, decidedAt)) {
                filter { eq("id", requestId) }
            }
    }

    /** Admin reject/restore of a time entry. */
    suspend fun updateTimeEntryApproval(
        timeEntryId: String, status: String,
        decidedByUserId: String?, decidedByName: String?, rejectionReason: String?
    ) {
        @kotlinx.serialization.Serializable
        data class ApprovalPatch(
            @kotlinx.serialization.SerialName("approval_status") val approvalStatus: String,
            @kotlinx.serialization.SerialName("decided_by_user_id") val decidedByUserId: String?,
            @kotlinx.serialization.SerialName("decided_by_name") val decidedByName: String?,
            @kotlinx.serialization.SerialName("decided_at") val decidedAt: String?,
            @kotlinx.serialization.SerialName("rejection_reason") val rejectionReason: String?,
            @kotlinx.serialization.SerialName("updated_at") val updatedAt: String
        )
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        client.postgrest.from("hf_time_entries")
            .update(ApprovalPatch(status, decidedByUserId, decidedByName, now, rejectionReason, now)) {
                filter { eq("id", timeEntryId) }
            }
    }

    /**
     * Insert a new row into `hf_squawks`. Returns the new row id so the
     * caller can correlate it with uploaded photos. Triggers an org
     * event afterward so every other device re-syncs within ~500ms.
     */
    @kotlinx.serialization.Serializable
    private data class NewSquawkRow(
        val id: String,
        val org_id: String,
        val plane_id: String?,
        val plane_tail_number: String,
        val title: String,
        val notes: String,
        val category: String,
        val status: String,
        val reported_by_user_id: String?,
        val reported_by_user_name: String?,
        val photo_paths: List<String>
    )

    @kotlinx.serialization.Serializable
    private data class NewTimeOffRow(
        val id: String,
        val org_id: String,
        val user_id: String,
        val user_name: String,
        val start_date: String,
        val end_date: String,
        val reason: String,
        val status: String,
        val decided_by_user_id: String? = null,
        val decided_by_name: String? = null,
        val decided_at: String? = null
    )

    /**
     * Submit a PTO request. Default status `pending` (tech flow).
     * Admins call with `autoApprove=true` so their own days off land
     * straight on the calendar without a self-approval round-trip.
     * `startDateIso` / `endDateIso` are YYYY-MM-DD (Postgres date type).
     */
    suspend fun createTimeOffRequest(
        orgId: String,
        userId: String,
        userName: String,
        startDateIso: String,
        endDateIso: String,
        reason: String,
        sourceDevice: String,
        autoApprove: Boolean = false
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val nowIso = if (autoApprove) java.time.Instant.now().toString() else null
        client.postgrest.from("hf_time_off_requests").insert(
            NewTimeOffRow(
                id = id,
                org_id = orgId,
                user_id = userId,
                user_name = userName,
                start_date = startDateIso,
                end_date = endDateIso,
                reason = reason,
                status = if (autoApprove) "approved" else "pending",
                decided_by_user_id = if (autoApprove) userId else null,
                decided_by_name = if (autoApprove) userName else null,
                decided_at = nowIso
            )
        )
        emitOrgEvent(orgId = orgId, sourceDevice = sourceDevice, eventType = "time_off_requested")
        return id
    }

    /** Pulls every PTO row for the org so admins can see pending +
     *  decided requests and approve/deny them. */
    suspend fun fetchTimeOffRequests(orgId: String): List<com.hangarflow.app.data.model.HFTimeOffRequest> =
        client.postgrest.from("hf_time_off_requests")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    // ── Calendar events ──
    suspend fun fetchCalendarEvents(orgId: String): List<com.hangarflow.app.data.model.HFCalendarEvent> =
        client.postgrest.from("hf_calendar_events")
            .select { filter { eq("org_id", orgId) } }
            .decodeList()

    suspend fun upsertCalendarEvent(event: com.hangarflow.app.data.model.HFCalendarEvent) {
        client.postgrest.from("hf_calendar_events").upsert(event)
    }

    suspend fun deleteCalendarEvent(eventId: String) {
        client.postgrest.from("hf_calendar_events").delete { filter { eq("id", eventId) } }
    }

    suspend fun createSquawk(
        orgId: String,
        planeId: String?,
        planeTailNumber: String,
        title: String,
        notes: String,
        category: String = "general",
        reportedByUserId: String?,
        reportedByUserName: String?,
        photoPaths: List<String>,
        sourceDevice: String
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val row = NewSquawkRow(
            id = id,
            org_id = orgId,
            plane_id = planeId,
            plane_tail_number = planeTailNumber,
            title = title,
            notes = notes,
            category = category,
            status = "open",
            reported_by_user_id = reportedByUserId,
            reported_by_user_name = reportedByUserName,
            photo_paths = photoPaths
        )
        client.postgrest.from("hf_squawks").insert(row)
        emitOrgEvent(orgId = orgId, sourceDevice = sourceDevice, eventType = "squawk_created")
        return id
    }

    /**
     * Single-column update on a work log. Used when a tech flips status
     * (e.g. Open → In Progress → Done) from the Android tablet. The RLS
     * policy already permits this for any org member, so no extra auth
     * check here — Supabase will 403 if the caller isn't in the org.
     */
    suspend fun updateWorkLogStatus(id: String, status: String) {
        client.postgrest
            .from("hf_work_logs")
            .update(mapOf("status" to status)) {
                filter { eq("id", id) }
            }
    }

    @kotlinx.serialization.Serializable
    private data class WorkLogPinUpdate(val pinned_at: String?)

    /** Pin or unpin a work log. Pin → now(); unpin → null. */
    suspend fun setWorkLogPinned(id: String, pinned: Boolean) {
        val payload = WorkLogPinUpdate(
            pinned_at = if (pinned) java.time.Instant.now().toString() else null
        )
        client.postgrest
            .from("hf_work_logs")
            .update(payload) {
                filter { eq("id", id) }
            }
    }

    @kotlinx.serialization.Serializable
    private data class WorkLogAssigneeUpdate(
        val assigned_user_id: String?,
        val assigned_user_name: String?
    )

    /** Reassign a work log. Pass `null` for both id and name to unassign. */
    suspend fun updateWorkLogAssignee(
        id: String,
        assignedUserId: String?,
        assignedUserName: String?
    ) {
        client.postgrest.from("hf_work_logs").update(
            WorkLogAssigneeUpdate(
                assigned_user_id = assignedUserId,
                assigned_user_name = assignedUserName
            )
        ) {
            filter { eq("id", id) }
        }
    }

    /**
     * Emits a tiny row into `hf_org_events` so other connected devices
     * (iPhone, Mac, other Android tablets) receive a Realtime INSERT and
     * know to re-sync. The `sourceDevice` lets the emitter filter out
     * its own events on the receive side.
     */
    suspend fun emitOrgEvent(
        orgId: String,
        sourceDevice: String,
        eventType: String = "snapshot_push"
    ) {
        client.postgrest
            .from("hf_org_events")
            .insert(
                mapOf(
                    "org_id" to orgId,
                    "event_type" to eventType,
                    "source_device" to sourceDevice
                )
            )
    }

    suspend fun cleanupOldEvents(orgId: String) {
        // Delete events older than 7 days to prevent unbounded growth
        val cutoff = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS).toString()
        runCatching {
            client.postgrest.from("hf_org_events").delete {
                filter {
                    eq("org_id", orgId)
                    lt("created_at", cutoff)
                }
            }
        }
    }

    /**
     * Emits one value for every INSERT on `hf_org_events` filtered to the
     * given org, with self-originated events filtered out via `ownDevice`.
     * Any downstream consumer that collects this flow will be notified
     * within ~500ms of another device pushing a change, matching the iOS
     * push-to-resync model.
     *
     * The Channel subscription is scoped to the collecting coroutine —
     * cancelling that coroutine cleans up the Realtime socket.
     */
    fun orgEventFlow(orgId: String, ownDevice: String): Flow<OrgEvent> {
        val channel = client.channel("hf-org-events-$orgId")
        val postgresFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "hf_org_events"
            filter("org_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, orgId)
        }
        return postgresFlow
            .onStart { channel.subscribe(blockUntilSubscribed = true) }
            .onCompletion {
                runCatching { channel.unsubscribe() }
            }
            .map { action ->
                OrgEvent(
                    eventType = action.record["event_type"]?.toString()?.trim('"') ?: "",
                    sourceDevice = action.record["source_device"]?.toString()?.trim('"')
                )
            }
            .filter { it.sourceDevice != ownDevice }
    }

    data class OrgEvent(val eventType: String, val sourceDevice: String?)

    // ---------- Admin create flows ----------

    @kotlinx.serialization.Serializable
    private data class NewPlaneRow(
        val id: String,
        val org_id: String,
        val tail_number: String,
        val display_name: String,
        val outline_hex: String
    )

    /** Insert a plane row. Caller should pre-uppercase the tail number and
     *  pick an outline hex (or leave `#FFFFFF` and server will default). */
    suspend fun createPlane(
        orgId: String,
        tailNumber: String,
        displayName: String,
        outlineHex: String
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val row = NewPlaneRow(
            id = id,
            org_id = orgId,
            tail_number = tailNumber.trim().uppercase(),
            display_name = displayName.trim().ifBlank { tailNumber.trim().uppercase() },
            outline_hex = outlineHex.ifBlank { "#FFFFFF" }
        )
        client.postgrest.from("hf_aircraft").insert(row)
        return id
    }

    @kotlinx.serialization.Serializable
    private data class NewWorkLogRow(
        val id: String,
        val org_id: String,
        val plane_id: String?,
        val plane_tail_number: String,
        val title: String,
        val category: String,
        val status: String,
        val details: String,
        val created_by_user_id: String? = null,
        val created_by_user_name: String? = null
    )

    /** Insert a work log row. Category/status are validated against the
     *  app's enums on the UI side; server RLS ensures org membership. */
    suspend fun createWorkLog(
        orgId: String,
        planeId: String?,
        planeTailNumber: String,
        title: String,
        category: String,
        details: String,
        createdByUserId: String? = null,
        createdByUserName: String? = null
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val row = NewWorkLogRow(
            id = id,
            org_id = orgId,
            plane_id = planeId,
            plane_tail_number = planeTailNumber,
            title = title.trim(),
            category = category,
            status = "open",
            details = details.trim(),
            created_by_user_id = createdByUserId,
            created_by_user_name = createdByUserName
        )
        client.postgrest.from("hf_work_logs").insert(row)
        return id
    }

    @kotlinx.serialization.Serializable
    private data class InviteEmployeeRequest(
        val action: String,
        val orgId: String?,
        val email: String,
        val displayName: String,
        val role: String
    )

    /** Invoke the `manage-employee` edge function to send a magic-link
     *  invite and upsert the membership + profile rows. The function
     *  enforces admin-only on the server. */
    suspend fun inviteEmployee(
        orgId: String,
        email: String,
        displayName: String,
        role: String
    ): String {
        val body = InviteEmployeeRequest(
            action = "invite",
            orgId = orgId,
            email = email.trim().lowercase(),
            displayName = displayName.trim(),
            role = if (role.equals("admin", ignoreCase = true)) "admin" else "tech"
        )
        val resp = client.functions.invoke(function = "manage-employee", body = body)
        return resp.bodyAsText()
    }

    // ---------- AI parts search (parts-search edge function) ----------

    @kotlinx.serialization.Serializable
    data class AIPartsSearchRequest(
        val query: String,
        @kotlinx.serialization.SerialName("plane_tail") val planeTail: String? = null,
        @kotlinx.serialization.SerialName("aircraft_type") val aircraftType: String? = null
    )

    @kotlinx.serialization.Serializable
    data class AIPartsSearchResult(
        val answer: String = "",
        val model: String? = null
    )

    /** Ask the AI parts assistant. Retrieval + Claude happen server-side
     *  in the `parts-search` edge function; the Anthropic key never ships
     *  in this APK. supabase-kt forwards the live session JWT. */
    suspend fun aiPartsSearch(query: String, planeTail: String?, aircraftType: String?): AIPartsSearchResult {
        val body = AIPartsSearchRequest(query = query.trim(), planeTail = planeTail, aircraftType = aircraftType)
        val resp = client.functions.invoke(function = "parts-search", body = body)
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(AIPartsSearchResult.serializer(), resp.bodyAsText())
    }

    // ---------- AI parts FIND (parts-find edge function — structured) ----------

    @kotlinx.serialization.Serializable
    data class AIPartReference(
        val code: String? = null, val title: String = "",
        val page: Int? = null, val manual: String? = null,
    )
    @kotlinx.serialization.Serializable
    data class AIPartCandidate(
        @kotlinx.serialization.SerialName("part_number") val partNumber: String? = null,
        @kotlinx.serialization.SerialName("part_name") val partName: String = "",
        val reference: AIPartReference? = null,
        @kotlinx.serialization.SerialName("in_stock") val inStock: Boolean = false,
        val note: String = "",
    )
    @kotlinx.serialization.Serializable
    data class AIPartsFindRequest(
        val query: String,
        @kotlinx.serialization.SerialName("plane_id") val planeId: String? = null,
        @kotlinx.serialization.SerialName("plane_tail") val planeTail: String? = null,
        @kotlinx.serialization.SerialName("aircraft_type") val aircraftType: String? = null,
    )
    @kotlinx.serialization.Serializable
    data class AIPartsFindResult(val parts: List<AIPartCandidate> = emptyList(), val note: String = "")

    /** Structured AI part finder — part name+number+manual ref to verify, plane-scoped. */
    suspend fun aiPartsFind(query: String, planeId: String?, planeTail: String?, aircraftType: String?): AIPartsFindResult {
        val resp = client.functions.invoke(
            function = "parts-find",
            body = AIPartsFindRequest(query.trim(), planeId, planeTail, aircraftType),
        )
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(AIPartsFindResult.serializer(), resp.bodyAsText())
    }

    // ---------- AI organize (organize-worklogs edge function) ----------

    @kotlinx.serialization.Serializable
    data class OrganizeRequest(
        @kotlinx.serialization.SerialName("worklog_id") val worklogId: String? = null,
        val limit: Int = 25,
    )
    @kotlinx.serialization.Serializable
    data class OrganizeResult(val enriched: Int = 0)

    /** Run the AI organize pass. worklogId enriches one log; null = batch. */
    suspend fun organizeWorkLogs(worklogId: String? = null, limit: Int = 25): Int {
        val resp = client.functions.invoke(
            function = "organize-worklogs",
            body = OrganizeRequest(worklogId = worklogId, limit = limit),
        )
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(OrganizeResult.serializer(), resp.bodyAsText()).enriched
    }

    // ---------- Org AI-indexing toggle (organizations.ai_indexing_enabled) ----------

    @kotlinx.serialization.Serializable
    private data class OrgAIFlagRow(
        @kotlinx.serialization.SerialName("ai_indexing_enabled") val aiIndexingEnabled: Boolean? = null,
    )

    suspend fun fetchAIIndexingEnabled(orgId: String): Boolean =
        client.postgrest.from("organizations")
            .select(io.github.jan.supabase.postgrest.query.Columns.list("ai_indexing_enabled")) {
                filter { eq("id", orgId) }
            }
            .decodeList<OrgAIFlagRow>()
            .firstOrNull()?.aiIndexingEnabled ?: false

    suspend fun setAIIndexingEnabled(orgId: String, enabled: Boolean) {
        client.postgrest.from("organizations")
            .update(mapOf("ai_indexing_enabled" to enabled)) { filter { eq("id", orgId) } }
    }

    // ---------- AI navigator (ai-navigate edge function) ----------

    @kotlinx.serialization.Serializable
    data class NavDestination(val key: String, val label: String, val hint: String? = null)

    @kotlinx.serialization.Serializable
    data class AINavigateRequest(val query: String, val destinations: List<NavDestination>)

    @kotlinx.serialization.Serializable
    data class AINavigateResult(val key: String? = null, val note: String = "")

    /** Ask the AI navigator which screen to open. Routing only — the model
     *  only ever sees the destination list (no web, no app data). */
    suspend fun aiNavigate(query: String, destinations: List<NavDestination>): AINavigateResult {
        val body = AINavigateRequest(query = query.trim(), destinations = destinations)
        val resp = client.functions.invoke(function = "ai-navigate", body = body)
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(AINavigateResult.serializer(), resp.bodyAsText())
    }

    // ---------- AI clock-out summary (clockout-summary edge function) ----------

    @kotlinx.serialization.Serializable
    data class ClockoutWorkLog(val title: String, val category: String? = null, val plane: String? = null, val details: String? = null, val minutes: Int? = null)

    @kotlinx.serialization.Serializable
    data class ClockoutSquawk(val title: String, val plane: String? = null, val status: String? = null)

    @kotlinx.serialization.Serializable
    data class ClockoutSummaryRequest(
        @kotlinx.serialization.SerialName("tech_name") val techName: String,
        @kotlinx.serialization.SerialName("minutes_worked") val minutesWorked: Int? = null,
        @kotlinx.serialization.SerialName("work_logs") val workLogs: List<ClockoutWorkLog>,
        val squawks: List<ClockoutSquawk>
    )

    @kotlinx.serialization.Serializable
    data class ClockoutSummaryResult(val summary: String = "")

    suspend fun clockoutSummary(techName: String, minutesWorked: Int?, workLogs: List<ClockoutWorkLog>, squawks: List<ClockoutSquawk>): ClockoutSummaryResult {
        val body = ClockoutSummaryRequest(techName, minutesWorked, workLogs, squawks)
        val resp = client.functions.invoke(function = "clockout-summary", body = body)
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(ClockoutSummaryResult.serializer(), resp.bodyAsText())
    }

    // ---------- AI squawk triage (squawk-triage edge function) ----------

    @kotlinx.serialization.Serializable
    data class SquawkTriageRequest(
        val title: String, val notes: String? = null, val category: String? = null,
        @kotlinx.serialization.SerialName("plane_tail") val planeTail: String? = null,
        @kotlinx.serialization.SerialName("aircraft_type") val aircraftType: String? = null
    )

    @kotlinx.serialization.Serializable
    data class SquawkTriageResult(
        @kotlinx.serialization.SerialName("ata_chapter") val ataChapter: String = "",
        @kotlinx.serialization.SerialName("problem_statement") val problemStatement: String = "",
        val parts: List<String> = emptyList(),
        val severity: String = "Routine",
        @kotlinx.serialization.SerialName("draft_title") val draftTitle: String = "",
        val vague: Boolean = false,
        val ask: String = "",
        val note: String = ""
    )

    suspend fun squawkTriage(title: String, notes: String?, category: String?, planeTail: String?, aircraftType: String?): SquawkTriageResult {
        val body = SquawkTriageRequest(title.trim(), notes, category, planeTail, aircraftType)
        val resp = client.functions.invoke(function = "squawk-triage", body = body)
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(SquawkTriageResult.serializer(), resp.bodyAsText())
    }

    // ---------- AI receipt extraction (receipt-extract edge function) ----------

    @kotlinx.serialization.Serializable
    data class ReceiptExtractRequest(
        @kotlinx.serialization.SerialName("image_base64") val imageBase64: String,
        @kotlinx.serialization.SerialName("media_type") val mediaType: String = "image/jpeg"
    )

    @kotlinx.serialization.Serializable
    data class ReceiptExtractResult(
        @kotlinx.serialization.SerialName("amount_cents") val amountCents: Int? = null,
        val vendor: String = "",
        val date: String = "",
        val note: String = ""
    )

    suspend fun extractReceipt(imageBase64: String, mediaType: String): ReceiptExtractResult {
        val body = ReceiptExtractRequest(imageBase64, mediaType)
        val resp = client.functions.invoke(function = "receipt-extract", body = body)
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(ReceiptExtractResult.serializer(), resp.bodyAsText())
    }

    // ---------- AI hours anomalies (hours-anomalies edge function) ----------

    @kotlinx.serialization.Serializable
    data class HoursAnomalyEntry(
        val id: String,
        @kotlinx.serialization.SerialName("user_name") val userName: String? = null,
        @kotlinx.serialization.SerialName("entry_date") val entryDate: String? = null,
        @kotlinx.serialization.SerialName("minutes_worked") val minutesWorked: Int? = null,
        val notes: String? = null
    )

    @kotlinx.serialization.Serializable
    data class HoursAnomaliesRequest(val entries: List<HoursAnomalyEntry>)

    @kotlinx.serialization.Serializable
    data class HoursFlag(val id: String, val reason: String = "", val severity: String = "info")

    @kotlinx.serialization.Serializable
    data class HoursAnomaliesResult(val flags: List<HoursFlag> = emptyList())

    suspend fun hoursAnomalies(entries: List<HoursAnomalyEntry>): List<HoursFlag> {
        if (entries.isEmpty()) return emptyList()
        val resp = client.functions.invoke(function = "hours-anomalies", body = HoursAnomaliesRequest(entries))
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(HoursAnomaliesResult.serializer(), resp.bodyAsText()).flags
    }

    // ---------- Delete operations ----------

    suspend fun deletePlane(planeId: String) {
        client.postgrest.from("hf_aircraft").delete { filter { eq("id", planeId) } }
    }

    suspend fun deleteOpenWorkLogsForPlane(planeId: String) {
        client.postgrest.from("hf_work_logs").delete {
            filter { eq("plane_id", planeId); neq("status", "done") }
        }
    }

    suspend fun deleteManualsForPlane(planeId: String) {
        client.postgrest.from("hf_manuals").delete { filter { eq("plane_id", planeId) } }
    }

    suspend fun deleteManualReferencesForPlane(planeTailNumber: String) {
        client.postgrest.from("hf_manual_references").delete { filter { eq("plane_tail_number", planeTailNumber) } }
    }

    suspend fun deleteSquawksForPlane(planeId: String) {
        client.postgrest.from("hf_squawks").delete { filter { eq("plane_id", planeId) } }
    }

    suspend fun deleteTasksForPlane(planeId: String) {
        client.postgrest.from("hf_tasks").delete { filter { eq("plane_id", planeId) } }
    }

    suspend fun deletePartRequestsForPlane(planeId: String) {
        client.postgrest.from("hf_part_requests").delete { filter { eq("plane_id", planeId) } }
    }

    suspend fun deleteWorkLog(id: String) {
        client.postgrest.from("hf_work_logs").delete { filter { eq("id", id) } }
    }

    suspend fun deleteSquawk(id: String) {
        client.postgrest.from("hf_squawks").delete { filter { eq("id", id) } }
    }

    suspend fun deleteUserProfile(userId: String) {
        client.postgrest.from("hf_user_profiles").delete { filter { eq("id", userId) } }
    }

    suspend fun updateUserRole(userId: String, newRole: String) {
        client.postgrest.from("hf_user_profiles").update(mapOf("role" to newRole)) {
            filter { eq("id", userId) }
        }
    }

    suspend fun updateOrgName(orgId: String, name: String) {
        client.postgrest.from("organizations").update(mapOf("name" to name.trim())) {
            filter { eq("id", orgId) }
        }
    }

    @kotlinx.serialization.Serializable
    private data class UpdatePlaneRow(
        val tail_number: String, val display_name: String,
        val outline_hex: String, val arrival_date: String?, val deadline_date: String?,
        val incoming_inspection: String?, val aircraft_type: String?
    )

    suspend fun updatePlane(
        planeId: String, tailNumber: String, displayName: String,
        outlineHex: String, arrivalDate: String?, deadlineDate: String?,
        incomingInspection: String? = null, aircraftType: String? = null
    ) {
        client.postgrest.from("hf_aircraft").update(
            UpdatePlaneRow(tailNumber.trim().uppercase(), displayName.trim(), outlineHex, arrivalDate, deadlineDate, incomingInspection, aircraftType)
        ) { filter { eq("id", planeId) } }
    }

    @kotlinx.serialization.Serializable
    private data class TimesCyclesPatch(
        val airframe_hours: String?, val airframe_cycles: String?,
        val hobbs: String?, val tach: String?,
        val engine1_hours: String?, val engine1_cycles: String?,
        val engine2_hours: String?, val engine2_cycles: String?,
        val engine3_hours: String?, val engine3_cycles: String?,
        val prop1_hours: String?, val prop2_hours: String?,
        val apu_hours: String?, val apu_cycles: String?
    )

    /** Persist the optional Times & Cycles intake reference (pure data). */
    suspend fun updatePlaneTimesAndCycles(
        planeId: String,
        airframeHours: String?, airframeCycles: String?,
        hobbs: String?, tach: String?,
        engine1Hours: String?, engine1Cycles: String?,
        engine2Hours: String?, engine2Cycles: String?,
        engine3Hours: String?, engine3Cycles: String?,
        prop1Hours: String?, prop2Hours: String?,
        apuHours: String?, apuCycles: String?
    ) {
        client.postgrest.from("hf_aircraft").update(
            TimesCyclesPatch(
                airframeHours, airframeCycles, hobbs, tach,
                engine1Hours, engine1Cycles, engine2Hours, engine2Cycles,
                engine3Hours, engine3Cycles, prop1Hours, prop2Hours, apuHours, apuCycles
            )
        ) { filter { eq("id", planeId) } }
    }

    // ---------- Parity wave: archive, manual assignments, ref linking, export name ----------

    @kotlinx.serialization.Serializable
    private data class IsArchivedRow(val is_archived: Boolean)

    /** Archive / unarchive a plane (hides from active list, keeps all data). */
    suspend fun setPlaneArchived(planeId: String, archived: Boolean) {
        client.postgrest.from("hf_aircraft").update(IsArchivedRow(archived)) {
            filter { eq("id", planeId) }
        }
    }

    @kotlinx.serialization.Serializable
    private data class ManualPlaneAssignmentRow(
        val org_id: String,
        val manual_id: String,
        val plane_id: String,
        val plane_tail_number: String? = null
    )

    /** Attach an already-uploaded manual to a plane via the junction table
     *  (no re-upload). Mirrors the iOS / Desktop "use existing files" flow. */
    suspend fun attachManualToPlane(
        orgId: String, manualId: String, planeId: String, planeTailNumber: String?
    ) {
        client.postgrest.from("hf_manual_plane_assignments")
            .insert(ManualPlaneAssignmentRow(orgId, manualId, planeId, planeTailNumber))
    }

    /** Detach a single manual from a plane (junction-table delete). */
    suspend fun detachManualFromPlane(manualId: String, planeId: String) {
        client.postgrest.from("hf_manual_plane_assignments").delete {
            filter { eq("manual_id", manualId); eq("plane_id", planeId) }
        }
    }

    /** Manual ids assigned to a plane; caller resolves against cached manuals. */
    suspend fun fetchManualIdsForPlane(orgId: String, planeId: String): List<String> =
        client.postgrest.from("hf_manual_plane_assignments")
            .select { filter { eq("org_id", orgId); eq("plane_id", planeId) } }
            .decodeList<ManualPlaneAssignmentRow>()
            .map { it.manual_id }

    /** Purge a manual from the DB. Junction rows cascade; indexed references
     *  persist by design so they can be re-attached. */
    suspend fun deleteManual(manualId: String) {
        client.postgrest.from("hf_manuals").delete { filter { eq("id", manualId) } }
    }

    @kotlinx.serialization.Serializable
    private data class WorkLogReferenceUpdateRow(
        val reference_id: String?,
        val reference_code: String?,
        val reference_title: String?,
        val manual_page_start: Int?,
        val manual_page_end: Int?,
        val manual_source_name: String?
    )

    /** Persist a manual-reference link chosen from the work-log detail
     *  "possible matches" panel. Pass nulls to clear the link. */
    suspend fun updateWorkLogReference(
        workLogId: String,
        referenceId: String?,
        referenceCode: String?,
        referenceTitle: String?,
        pageStart: Int?,
        pageEnd: Int?,
        sourceName: String?
    ) {
        client.postgrest.from("hf_work_logs").update(
            WorkLogReferenceUpdateRow(referenceId, referenceCode, referenceTitle, pageStart, pageEnd, sourceName)
        ) { filter { eq("id", workLogId) } }
    }

    @kotlinx.serialization.Serializable
    private data class OrgExportNameRow(
        @kotlinx.serialization.SerialName("export_business_name") val exportBusinessName: String? = null
    )

    /** Org's optional "doing-business-as" name for PDF export headers
     *  (falls back to org name when null). */
    suspend fun fetchOrgExportBusinessName(orgId: String): String? =
        client.postgrest.from("organizations")
            .select { filter { eq("id", orgId) } }
            .decodeList<OrgExportNameRow>()
            .firstOrNull()
            ?.exportBusinessName

}
