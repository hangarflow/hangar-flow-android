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
        val updated_by_user_name: String
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
        updatedByUserName: String
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
            updated_by_user_name = updatedByUserName.trim()
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
        updatedByUserName: String
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
            updated_by_user_name = updatedByUserName.trim()
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

    suspend fun searchManualReferences(orgId: String, query: String): List<ManualSearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
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
        val details: String
    )

    /** Insert a work log row. Category/status are validated against the
     *  app's enums on the UI side; server RLS ensures org membership. */
    suspend fun createWorkLog(
        orgId: String,
        planeId: String?,
        planeTailNumber: String,
        title: String,
        category: String,
        details: String
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
            details = details.trim()
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
        val outline_hex: String, val arrival_date: String?, val deadline_date: String?
    )

    suspend fun updatePlane(
        planeId: String, tailNumber: String, displayName: String,
        outlineHex: String, arrivalDate: String?, deadlineDate: String?
    ) {
        client.postgrest.from("hf_aircraft").update(
            UpdatePlaneRow(tailNumber.trim().uppercase(), displayName.trim(), outlineHex, arrivalDate, deadlineDate)
        ) { filter { eq("id", planeId) } }
    }

}
