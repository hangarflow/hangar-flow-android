package com.hangarflow.app.data

import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import com.hangarflow.app.data.cloud.TimeEntryService
import com.hangarflow.app.data.model.HFAuditEvent
import com.hangarflow.app.data.model.HFManual
import com.hangarflow.app.data.model.HFPartLocation
import com.hangarflow.app.data.model.HFPartRequest
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.data.model.HFSquawk
import com.hangarflow.app.data.model.HFTask
import com.hangarflow.app.data.model.HFTimeEntry
import com.hangarflow.app.data.model.HFUserProfile
import com.hangarflow.app.data.model.HFWorkLog
import io.github.jan.supabase.auth.auth
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Kotlin port of iOS `HFSharedStore`. Single process-wide store that
 * every screen observes via StateFlow. The Supabase client writes land
 * here, and Realtime INSERTs push into here, so the UI is always a pure
 * function of the store state.
 *
 * Phase 5c: Realtime subscription live. When any other device pushes a
 * snapshot, we re-pull within ~500ms. No more polling.
 */
object SharedStore {
    private val cloud = HFCloudSyncService()
    private val timeEntryService = TimeEntryService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ShopState.empty())
    val state: StateFlow<ShopState> = _state.asStateFlow()

    private val _activeShift = MutableStateFlow<ActiveShift?>(null)
    val activeShift: StateFlow<ActiveShift?> = _activeShift.asStateFlow()

    /**
     * In-process device identifier used to filter out our own realtime
     * events. Not persisted — a fresh UUID per app launch is plenty
     * because the only use is "did the push come from this VM?"
     */
    private val deviceId: String = UUID.randomUUID().toString()

    /** Exposed so writers (create squawk, update work log, etc.) can pass
     *  the same device ID when emitting org events — that's how each
     *  device filters out its own realtime pushes. */
    fun deviceIdentifier(): String = deviceId

    private var bootstrappedOrgId: String? = null
    private var realtimeJob: Job? = null

    /**
     * Pulls planes + work logs for the given org and starts a Realtime
     * subscription that re-pulls whenever another device pushes changes.
     */
    fun bootstrap(orgId: String, force: Boolean = false) {
        if (!force && bootstrappedOrgId == orgId) return
        bootstrappedOrgId = orgId

        // Hydrate from disk first so the UI shows something useful
        // instantly even if the hangar's wifi is flaky. The cloud pull
        // will overwrite this as soon as it completes.
        val cached = OfflineCache.load(orgId)
        if (cached != null) {
            _state.value = cached.copy(loading = true, error = null)
        } else {
            _state.update { it.copy(loading = true, error = null) }
        }
        // Restore any in-progress shift so the clock-in timer survives
        // app exits. If there's nothing on disk, _activeShift stays null.
        if (_activeShift.value == null) {
            _activeShift.value = ShiftPersistence.load()
        }
        scope.launch {
            pullSnapshot(orgId)
            // Housekeeping: prune org events older than 7 days to
            // prevent unbounded table growth in production.
            runCatching { cleanupOldOrgEvents(orgId) }
        }
        startRealtime(orgId)
    }

    private suspend fun cleanupOldOrgEvents(orgId: String) {
        runCatching {
            cloud.cleanupOldEvents(orgId)
        }
    }

    /** Manual "pull to refresh" hook — also used by the Mac refresh button. */
    fun refresh() {
        val orgId = bootstrappedOrgId ?: return
        scope.launch { pullSnapshot(orgId) }
    }

    // AI organize (work logs)
    private val _aiIndexingEnabled = MutableStateFlow(false)
    val aiIndexingEnabled: StateFlow<Boolean> = _aiIndexingEnabled.asStateFlow()
    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    /** Run the AI organize pass for one work log, then re-pull so the new
     *  reference link + recommended parts appear. Returns count enriched. */
    suspend fun organizeWorkLog(id: String): Int {
        val orgId = bootstrappedOrgId ?: return 0
        _isOrganizing.value = true
        val n = try { cloud.organizeWorkLogs(worklogId = id) } catch (t: Throwable) { 0 }
        _isOrganizing.value = false
        runCatching { pullSnapshot(orgId) }
        return n
    }

    /** Load the org's AI-indexing toggle into [aiIndexingEnabled]. */
    suspend fun loadAIIndexingFlag() {
        val orgId = bootstrappedOrgId ?: return
        runCatching { cloud.fetchAIIndexingEnabled(orgId) }.getOrNull()?.let { _aiIndexingEnabled.value = it }
    }

    /** Flip the org's AI-indexing toggle (admin). */
    suspend fun setAIIndexing(enabled: Boolean) {
        val orgId = bootstrappedOrgId ?: return
        _aiIndexingEnabled.value = enabled
        runCatching { cloud.setAIIndexingEnabled(orgId, enabled) }
    }

    /**
     * Optimistically flip a work log's status in local state, push the
     * change to Supabase, then emit an org event so other devices
     * (iPhone / Mac) re-sync within ~500ms.
     *
     * On failure the local optimistic change is rolled back and the
     * error surfaces in `state.error`.
     */
    fun updateWorkLogStatus(workLogId: String, newStatus: String) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.workLogs.firstOrNull { it.id == workLogId }?.status
        // Optimistic UI update
        _state.update { current ->
            current.copy(
                workLogs = current.workLogs.map { log ->
                    if (log.id == workLogId) log.copy(status = newStatus) else log
                }
            )
        }
        scope.launch {
            try {
                cloud.updateWorkLogStatus(workLogId, newStatus)
                cloud.emitOrgEvent(orgId = orgId, sourceDevice = deviceId)
            } catch (t: Throwable) {
                // Roll back optimistic change
                _state.update { current ->
                    current.copy(
                        workLogs = current.workLogs.map { log ->
                            if (log.id == workLogId && previous != null) log.copy(status = previous) else log
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    fun clear() {
        realtimeJob?.cancel()
        realtimeJob = null
        val orgId = bootstrappedOrgId
        bootstrappedOrgId = null
        _state.value = ShopState.empty()
        _activeShift.value = null
        ShiftPersistence.clear()
        // Blow away the on-disk cache — next sign-in will repopulate.
        if (orgId != null) OfflineCache.clear(orgId)
    }

    // -------- Squawk → Task conversion --------

    /**
     * Spawn an HFTask off a squawk and flip the squawk's status to
     * `convertedToTask`. Mirrors the iOS flow so techs on the Android
     * tablet can promote a squawk to a tracked task in one tap.
     */
    fun convertSquawkToTask(squawk: com.hangarflow.app.data.model.HFSquawk) {
        val orgId = bootstrappedOrgId ?: return
        val me = _state.value.currentUser
        val task = com.hangarflow.app.data.model.HFTask(
            id = java.util.UUID.randomUUID().toString(),
            orgId = orgId,
            planeId = squawk.planeId,
            planeTailNumber = squawk.planeTailNumber,
            title = squawk.title,
            details = squawk.notes,
            category = squawk.category,
            status = "open",
            assignedUserId = me?.id,
            assignedUserName = me?.displayName,
            linkedSquawkId = squawk.id,
            isFromSquawk = true,
            waitingOnParts = false,
            loggedMinutes = 0
        )
        // Optimistic local update: append the task and flip the squawk
        // status so the UI reflects immediately. Rollback both on failure.
        val prevStatus = squawk.status
        _state.update { current ->
            current.copy(
                tasks = current.tasks + task,
                squawks = current.squawks.map {
                    if (it.id == squawk.id) it.copy(status = "convertedToTask") else it
                }
            )
        }
        scope.launch {
            try {
                cloud.upsertTask(task)
                cloud.updateSquawkStatus(squawk.id, "convertedToTask")
                cloud.emitOrgEvent(orgId = orgId, sourceDevice = deviceId, eventType = "squawk_converted_to_task")
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        tasks = current.tasks.filterNot { it.id == task.id },
                        squawks = current.squawks.map {
                            if (it.id == squawk.id) it.copy(status = prevStatus) else it
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    // -------- Task status updates --------

    fun updateTaskStatus(taskId: String, newStatus: String) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.tasks.firstOrNull { it.id == taskId }?.status
        _state.update { current ->
            current.copy(
                tasks = current.tasks.map {
                    if (it.id == taskId) it.copy(status = newStatus) else it
                }
            )
        }
        scope.launch {
            try {
                cloud.updateTaskStatus(taskId, newStatus)
                cloud.emitOrgEvent(orgId = orgId, sourceDevice = deviceId, eventType = "task_updated")
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        tasks = current.tasks.map {
                            if (it.id == taskId && previous != null) it.copy(status = previous) else it
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    // -------- Squawk status updates --------

    fun updateSquawkStatus(squawkId: String, newStatus: String) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.squawks.firstOrNull { it.id == squawkId }?.status
        _state.update { current ->
            current.copy(
                squawks = current.squawks.map {
                    if (it.id == squawkId) it.copy(status = newStatus) else it
                }
            )
        }
        scope.launch {
            try {
                cloud.updateSquawkStatus(squawkId, newStatus)
                cloud.emitOrgEvent(orgId = orgId, sourceDevice = deviceId, eventType = "squawk_updated")
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        squawks = current.squawks.map {
                            if (it.id == squawkId && previous != null) it.copy(status = previous) else it
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    // -------- Part request status updates --------

    fun updatePartRequestStatus(partRequestId: String, newStatus: String) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.partRequests.firstOrNull { it.id == partRequestId }?.status
        _state.update { current ->
            current.copy(
                partRequests = current.partRequests.map {
                    if (it.id == partRequestId) it.copy(status = newStatus) else it
                }
            )
        }
        scope.launch {
            try {
                cloud.updatePartRequestStatus(partRequestId, newStatus)
                cloud.emitOrgEvent(orgId = orgId, sourceDevice = deviceId, eventType = "part_request_updated")
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        partRequests = current.partRequests.map {
                            if (it.id == partRequestId && previous != null) it.copy(status = previous) else it
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    // -------- Work log progress / sign-off --------

    /**
     * Record work-log progress the tech just logged: minutes worked,
     * initials, parts used. Creates a `hf_time_entries` row linked to
     * the work log. If `signOff` is true, also flips the work log's
     * status to `done` so it falls off the open-work queue. Both Mac
     * admin and iOS see the change via the normal realtime push.
     */
    fun saveWorkLogProgress(
        workLog: HFWorkLog,
        minutes: Int,
        initials: String,
        partsSummary: String,
        signOff: Boolean
    ) {
        val orgId = bootstrappedOrgId ?: return
        val me = _state.value.currentUser
        val notes = listOfNotNull(
            initials.trim().takeIf { it.isNotBlank() }?.let { "Initials: $it" },
            partsSummary.trim().takeIf { it.isNotBlank() }?.let { "Parts: $it" }
        ).joinToString(" • ")

        scope.launch {
            try {
                if (minutes > 0) {
                    timeEntryService.createTimeEntry(
                        orgId = orgId,
                        userId = me?.id,
                        userName = me?.displayName?.ifBlank { "Tech" } ?: "Tech",
                        planeId = workLog.planeId,
                        planeTailNumber = workLog.planeTailNumber.ifBlank { null },
                        entryDateIso = Instant.now().toString(),
                        minutesWorked = minutes,
                        notes = notes,
                        linkedWorkLogId = workLog.id
                    )
                }
                if (signOff) {
                    cloud.updateWorkLogStatus(workLog.id, "done")
                    _state.update { current ->
                        current.copy(
                            workLogs = current.workLogs.map {
                                if (it.id == workLog.id) it.copy(status = "done") else it
                            }
                        )
                    }
                }
                cloud.emitOrgEvent(
                    orgId = orgId,
                    sourceDevice = deviceId,
                    eventType = if (signOff) "work_log_signed_off" else "work_log_progress_saved"
                )
                pullSnapshot(orgId)
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Failed to save progress.") }
            }
        }
    }

    // -------- Time entries (Clock In / Clock Out) --------

    /**
     * Start a shift. Tracks the in-memory start time so we can compute
     * minutes worked on clock-out. No DB write happens until the user
     * clocks out — matches iOS behavior where a shift is a local
     * placeholder until complete.
     */
    fun clockIn() {
        val me = _state.value.currentUser
        val userName = me?.displayName?.takeIf { it.isNotBlank() } ?: "Tech"
        val shift = ActiveShift(
            userId = me?.id,
            userName = userName,
            startedAt = Instant.now()
        )
        _activeShift.value = shift
        ShiftPersistence.save(shift)
    }

    /** Start lunch — freezes the working timer until `lunchIn()`. */
    fun lunchOut() {
        val shift = _activeShift.value ?: return
        if (shift.lunchStartedAt != null || shift.lunchTaken) return
        val next = shift.copy(lunchStartedAt = Instant.now())
        _activeShift.value = next
        ShiftPersistence.save(next)
    }

    /** Return from lunch — accumulate lunch minutes and resume working. */
    fun lunchIn() {
        val shift = _activeShift.value ?: return
        val lunchStart = shift.lunchStartedAt ?: return
        val lunchMinutes = ChronoUnit.MINUTES
            .between(lunchStart, Instant.now())
            .toInt()
            .coerceAtLeast(0)
        val next = shift.copy(
            lunchStartedAt = null,
            lunchMinutesAccrued = shift.lunchMinutesAccrued + lunchMinutes,
            lunchTaken = true
        )
        _activeShift.value = next
        ShiftPersistence.save(next)
    }

    /**
     * Mark lunch as skipped for the active shift. Sets `lunchTaken = true`
     * so the lunch prompt goes away — the tech keeps working straight
     * through and clocks out normally at end of day. Does NOT clock out.
     */
    fun skipLunch() {
        val shift = _activeShift.value ?: return
        if (shift.lunchTaken || shift.lunchStartedAt != null) return
        val next = shift.copy(lunchTaken = true)
        _activeShift.value = next
        ShiftPersistence.save(next)
    }

    /**
     * End the active shift. Total shift minutes minus any lunch minutes
     * become the entry. If the tech somehow clocks out while still on
     * lunch we close the lunch bracket first so the math stays honest.
     */
    fun clockOut() {
        scope.launch { clockOutWithSummary("") }
    }

    /**
     * Awaited version of clockOut that lets the caller (the clock-out
     * sheet) pass a daily summary and chain reimbursement inserts off
     * the returned time entry id. Returns null if there's no active
     * shift or no org loaded. The summary string is appended to the
     * lunch suffix so the admin sees both in one notes column.
     */
    suspend fun clockOutWithSummary(summary: String): String? {
        val shift = _activeShift.value ?: return null
        val orgId = bootstrappedOrgId ?: return null
        val end = Instant.now()
        val totalMin = ChronoUnit.MINUTES.between(shift.startedAt, end).toInt().coerceAtLeast(1)
        val tailLunch = shift.lunchStartedAt?.let {
            ChronoUnit.MINUTES.between(it, end).toInt().coerceAtLeast(0)
        } ?: 0
        val totalLunch = shift.lunchMinutesAccrued + tailLunch
        val minutes = (totalMin - totalLunch).coerceAtLeast(1)
        _activeShift.value = null
        ShiftPersistence.clear()
        return try {
            val trimmedSummary = summary.trim()
            val lunchPart = if (totalLunch > 0) "Lunch: ${formatLunch(totalLunch)}" else ""
            val notes = listOf(trimmedSummary, lunchPart)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            val entry = timeEntryService.createTimeEntry(
                orgId = orgId,
                userId = shift.userId,
                userName = shift.userName,
                planeId = null,
                planeTailNumber = null,
                entryDateIso = end.toString(),
                minutesWorked = minutes,
                notes = notes
            )
            cloud.emitOrgEvent(orgId = orgId, sourceDevice = deviceId, eventType = "time_entry_logged")
            // Local refresh so the Today tile ticks up immediately —
            // don't wait for the realtime self-event (we filter
            // self-events out intentionally).
            pullSnapshot(orgId)
            entry.id
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message ?: "Failed to save time entry.") }
            null
        }
    }

    // -------- Admin create flows --------

    /** Result wrapper so the sheet can show success / error feedback. */
    sealed class CreateResult {
        object Success : CreateResult()
        data class Error(val message: String) : CreateResult()
    }

    /** One staged row for the bulk "add work log" sheet. */
    data class NewWorkLogDraft(
        val planeId: String?,
        val planeTailNumber: String,
        val title: String,
        val category: String,
        val details: String
    )

    // ── Audit trail (paper trail) ──
    /** Append an audit event, fire-and-forget. Actor defaults to the
     *  current user. Never blocks or fails the caller. */
    fun logAudit(entityType: String, entityId: String?, action: String, summary: String, actorName: String? = null) {
        val orgId = bootstrappedOrgId ?: return
        val me = _state.value.currentUser
        scope.launch {
            runCatching {
                cloud.insertAuditEvent(orgId, entityType, entityId, action, me?.id, actorName ?: me?.displayName ?: "", summary)
            }
        }
    }

    /** Load the audit log (paper trail) for the Activity Log screen. */
    suspend fun fetchAuditEvents(limit: Int = 300): List<HFAuditEvent> {
        val orgId = bootstrappedOrgId ?: return emptyList()
        return runCatching { cloud.fetchAuditLog(orgId, limit) }.getOrDefault(emptyList())
    }

    // ── Admin approval flows (time-off, reimbursement, time entry) ──

    /** Admin approve/deny a PTO request. `approve=true` → approved,
     *  else denied. Optimistic local update + cloud write + audit. */
    fun decideTimeOffRequest(requestId: String, approve: Boolean) {
        val orgId = bootstrappedOrgId ?: return
        val me = _state.value.currentUser
        val status = if (approve) "approved" else "denied"
        val nowIso = java.time.Instant.now().toString()
        _state.update { st ->
            st.copy(timeOffRequests = st.timeOffRequests.map {
                if (it.id == requestId) it.copy(
                    status = status, decidedByUserId = me?.id,
                    decidedByName = me?.displayName, decidedAt = nowIso
                ) else it
            })
        }
        scope.launch {
            runCatching {
                cloud.decideTimeOffRequestRow(requestId, status, me?.id, me?.displayName, nowIso)
                cloud.emitOrgEvent(orgId, deviceId, "time_off_decided")
            }.onFailure { t -> _state.update { it.copy(error = t.message) } }
            logAudit("time_off", requestId, status, "Time-off request $status")
            pullSnapshot(orgId)
        }
    }

    /** Admin approve/deny a reimbursement. */
    fun decideReimbursement(reimbursementId: String, approve: Boolean) {
        val orgId = bootstrappedOrgId ?: return
        val me = _state.value.currentUser
        val status = if (approve) "approved" else "denied"
        val nowIso = java.time.Instant.now().toString()
        _state.update { st ->
            st.copy(reimbursements = st.reimbursements.map {
                if (it.id == reimbursementId) it.copy(
                    status = status, decidedByUserId = me?.id,
                    decidedByName = me?.displayName, decidedAt = nowIso
                ) else it
            })
        }
        scope.launch {
            runCatching {
                cloud.updateReimbursementStatus(reimbursementId, status, me?.id ?: "", me?.displayName ?: "")
                cloud.emitOrgEvent(orgId, deviceId, "reimbursement_decided")
            }.onFailure { t -> _state.update { it.copy(error = t.message) } }
            logAudit("reimbursement", reimbursementId, status, "Reimbursement $status")
            pullSnapshot(orgId)
        }
    }

    /** Admin reject/restore a time entry. `reject=true` → rejected with
     *  an optional reason; false restores it to approved. */
    fun decideTimeEntry(timeEntryId: String, reject: Boolean, reason: String? = null) {
        val orgId = bootstrappedOrgId ?: return
        val me = _state.value.currentUser
        val status = if (reject) "rejected" else "approved"
        val nowIso = java.time.Instant.now().toString()
        _state.update { st ->
            st.copy(timeEntries = st.timeEntries.map {
                if (it.id == timeEntryId) it.copy(
                    approvalStatus = status, decidedByUserId = me?.id,
                    decidedByName = me?.displayName, decidedAt = nowIso,
                    rejectionReason = if (reject) reason else null
                ) else it
            })
        }
        scope.launch {
            runCatching {
                cloud.updateTimeEntryApproval(
                    timeEntryId, status, me?.id, me?.displayName,
                    if (reject) reason else null
                )
                cloud.emitOrgEvent(orgId, deviceId, "time_entry_decided")
            }.onFailure { t -> _state.update { it.copy(error = t.message) } }
            logAudit("time_entry", timeEntryId, status, "Time entry $status")
            pullSnapshot(orgId)
        }
    }

    // ── Calendar events ──

    /** Admin-create a calendar event (org-wide or plane-scoped).
     *  Optimistic local insert + cloud upsert + org event. */
    suspend fun createCalendarEvent(
        title: String,
        description: String,
        startDate: String,
        endDate: String,
        planeId: String?,
        planeTailNumber: String?,
        colorHex: String?,
        eventKind: String = "general",
        visibility: String = "public",
        remindAt: String? = null,
        remindUserId: String? = null
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        val me = _state.value.currentUser
        val authUserId = runCatching {
            SupabaseClientProvider.client.auth.currentUserOrNull()?.id
        }.getOrNull()
        val event = com.hangarflow.app.data.model.HFCalendarEvent(
            id = java.util.UUID.randomUUID().toString(),
            orgId = orgId,
            title = title.trim(),
            description = description.trim(),
            startDate = startDate,
            endDate = endDate,
            planeId = planeId,
            planeTailNumber = planeTailNumber,
            colorHex = colorHex,
            eventKind = eventKind,
            createdByUserId = authUserId ?: me?.id,
            createdByUserName = me?.displayName ?: "Admin",
            visibility = if (visibility in setOf("public", "admin_only", "personal")) visibility else "public",
            remindAt = remindAt,
            remindUserId = remindUserId
        )
        return try {
            cloud.upsertCalendarEvent(event)
            _state.update { s -> s.copy(calendarEvents = (s.calendarEvents + event).sortedBy { it.startDate }) }
            cloud.emitOrgEvent(orgId, deviceId, "calendar_event_created")
            logAudit("calendar_event", event.id, "created", "Event \"${event.title}\" created")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) {
            CreateResult.Error(t.message ?: "Couldn't save the event.")
        }
    }

    /** Delete a calendar event. Optimistic local removal + cloud delete. */
    suspend fun deleteCalendarEvent(eventId: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.deleteCalendarEvent(eventId)
            _state.update { s -> s.copy(calendarEvents = s.calendarEvents.filterNot { it.id == eventId }) }
            cloud.emitOrgEvent(orgId, deviceId, "calendar_event_deleted")
            CreateResult.Success
        } catch (t: Throwable) {
            CreateResult.Error(t.message ?: "Couldn't delete the event.")
        }
    }

    /** Draft an end-of-shift summary for `userName` from the work logs they
     *  completed today + squawks they filed today. Returns null if there's
     *  nothing to summarize. */
    suspend fun draftClockOutSummary(userName: String): String? {
        val today = java.time.LocalDate.now().toString()
        val st = _state.value
        val myLogs = st.workLogs.filter {
            it.status == "done" && it.assignedUserName == userName &&
                (it.updatedAt ?: it.createdAt ?: "").startsWith(today)
        }
        val mySquawks = st.squawks.filter {
            it.reportedByUserName == userName && (it.createdAt ?: "").startsWith(today)
        }
        if (myLogs.isEmpty() && mySquawks.isEmpty()) return null
        val wl = myLogs.map {
            HFCloudSyncService.ClockoutWorkLog(
                title = it.title, category = it.category, plane = it.planeTailNumber,
                details = it.details.ifBlank { null }, minutes = it.loggedMinutes.takeIf { m -> m > 0 }
            )
        }
        val sq = mySquawks.map { HFCloudSyncService.ClockoutSquawk(it.title, it.planeTailNumber, it.status) }
        return runCatching { cloud.clockoutSummary(userName, null, wl, sq) }.getOrNull()?.summary
    }

    suspend fun createPlane(
        tailNumber: String,
        displayName: String,
        outlineHex: String
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded yet.")
        if (tailNumber.trim().isBlank()) return CreateResult.Error("Tail number is required.")
        val duplicate = _state.value.planes.any {
            it.tailNumber.equals(tailNumber.trim(), ignoreCase = true)
        }
        if (duplicate) return CreateResult.Error("A plane with that tail number already exists.")
        return try {
            cloud.createPlane(orgId, tailNumber, displayName, outlineHex)
            cloud.emitOrgEvent(orgId, deviceId, "plane_created")
            logAudit("plane", null, "created", "Added plane ${tailNumber.trim().uppercase()}")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) {
            CreateResult.Error(t.message ?: "Couldn't create plane.")
        }
    }

    suspend fun createWorkLog(
        planeId: String?,
        planeTailNumber: String,
        title: String,
        category: String,
        details: String
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded yet.")
        if (title.trim().isBlank()) return CreateResult.Error("Title is required.")
        val me = _state.value.currentUser  // paper trail
        return try {
            val wlId = cloud.createWorkLog(orgId, planeId, planeTailNumber, title, category, details, me?.id, me?.displayName)
            cloud.emitOrgEvent(orgId, deviceId, "work_log_created")
            logAudit("work_log", wlId, "created", "Added work log \"${title.trim()}\" to ${planeTailNumber.uppercase()}")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) {
            CreateResult.Error(t.message ?: "Couldn't create work log.")
        }
    }

    suspend fun inviteEmployee(
        email: String,
        displayName: String,
        role: String
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded yet.")
        if (email.trim().isBlank()) return CreateResult.Error("Email is required.")
        if (displayName.trim().isBlank()) return CreateResult.Error("Name is required.")
        return try {
            cloud.inviteEmployee(orgId, email, displayName, role)
            cloud.emitOrgEvent(orgId, deviceId, "user_invited")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) {
            CreateResult.Error(t.message ?: "Couldn't send invite.")
        }
    }

    // -------- Part location (shared hangar inventory) --------

    suspend fun savePartLocation(
        existingId: String?,
        partName: String,
        partNumber: String,
        serialNumber: String,
        location: String,
        quantity: Int,
        stockStatus: String,
        planeIds: List<String>,
        notes: String,
        photoPaths: List<String> = emptyList()
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded yet.")
        if (partName.trim().isBlank()) return CreateResult.Error("Part name is required.")
        val me = _state.value.currentUser
        return try {
            if (existingId == null) {
                cloud.createPartLocation(
                    orgId = orgId,
                    partName = partName,
                    partNumber = partNumber,
                    serialNumber = serialNumber,
                    location = location,
                    quantity = quantity,
                    stockStatus = stockStatus,
                    planeIds = planeIds,
                    notes = notes,
                    updatedByUserId = me?.id,
                    updatedByUserName = me?.displayName ?: "Tech",
                    photoPaths = photoPaths
                )
            } else {
                cloud.updatePartLocation(
                    id = existingId,
                    partName = partName,
                    partNumber = partNumber,
                    serialNumber = serialNumber,
                    location = location,
                    quantity = quantity,
                    stockStatus = stockStatus,
                    planeIds = planeIds,
                    notes = notes,
                    updatedByUserId = me?.id,
                    updatedByUserName = me?.displayName ?: "Tech",
                    photoPaths = photoPaths
                )
            }
            cloud.emitOrgEvent(orgId, deviceId, "part_location_saved")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) {
            CreateResult.Error(t.message ?: "Couldn't save part location.")
        }
    }

    // ---------- Delete operations (parity with Desktop) ----------

    suspend fun deletePlaneWithHistory(planeId: String, planeTailNumber: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            runCatching { cloud.deleteSquawksForPlane(planeId) }
            runCatching { cloud.deleteTasksForPlane(planeId) }
            runCatching { cloud.deletePartRequestsForPlane(planeId) }
            runCatching { cloud.deleteOpenWorkLogsForPlane(planeId) }
            runCatching { cloud.deleteManualsForPlane(planeId) }
            runCatching { cloud.deleteManualReferencesForPlane(planeTailNumber) }
            cloud.deletePlane(planeId)
            cloud.emitOrgEvent(orgId, deviceId, "plane_deleted")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't delete plane.") }
    }

    suspend fun deleteWorkLog(id: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.deleteWorkLog(id)
            cloud.emitOrgEvent(orgId, deviceId, "work_log_deleted")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't delete.") }
    }

    suspend fun deleteSquawk(id: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.deleteSquawk(id)
            cloud.emitOrgEvent(orgId, deviceId, "squawk_deleted")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't delete.") }
    }

    suspend fun deleteUser(userId: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.deleteUserProfile(userId)
            cloud.emitOrgEvent(orgId, deviceId, "user_deleted")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't delete user.") }
    }

    suspend fun updateUserRole(userId: String, newRole: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.updateUserRole(userId, newRole)
            cloud.emitOrgEvent(orgId, deviceId, "user_role_changed")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't update role.") }
    }

    /** Distinct aircraft types known to the org (from tagged manuals + planes). */
    fun knownAircraftTypes(): List<String> {
        val all = (_state.value.manuals.mapNotNull { it.aircraftType } +
                   _state.value.planes.mapNotNull { it.aircraftType })
            .map { it.trim() }.filter { it.isNotEmpty() }
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (t in all) if (seen.add(t.lowercase())) out.add(t)
        return out.sortedBy { it.lowercase() }
    }

    /** Manuals tagged with the given aircraft type (case-insensitive). */
    fun manualsForType(type: String): List<com.hangarflow.app.data.model.HFManual> {
        val t = type.trim().lowercase()
        if (t.isBlank()) return emptyList()
        return _state.value.manuals.filter { (it.aircraftType ?: "").trim().lowercase() == t }
    }

    suspend fun updatePlane(
        planeId: String, tailNumber: String, displayName: String,
        outlineHex: String, arrivalDate: String?, deadlineDate: String?,
        incomingInspection: String? = null, aircraftType: String? = null
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.updatePlane(planeId, tailNumber, displayName, outlineHex, arrivalDate, deadlineDate, incomingInspection, aircraftType)
            cloud.emitOrgEvent(orgId, deviceId, "plane_updated")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't update plane.") }
    }

    // ---------- Parity wave: archive, manual assignments, ref linking, bulk ----------

    /** Archive / unarchive a plane. Optimistic flip + org event so every
     *  other device re-syncs; rolls back on failure. */
    fun setPlaneArchived(planeId: String, archived: Boolean) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.planes.firstOrNull { it.id == planeId }?.isArchived
        _state.update { current ->
            current.copy(planes = current.planes.map { p ->
                if (p.id == planeId) p.copy(isArchived = archived) else p
            })
        }
        scope.launch {
            try {
                cloud.setPlaneArchived(planeId, archived)
                cloud.emitOrgEvent(orgId, deviceId, "plane_archived")
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        planes = current.planes.map { p ->
                            if (p.id == planeId && previous != null) p.copy(isArchived = previous) else p
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    /** Attach already-uploaded manuals to a plane (no re-upload). One org
     *  event + re-pull after the whole batch. */
    suspend fun attachManualsToPlane(
        planeId: String, planeTailNumber: String, manualIds: List<String>
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        if (manualIds.isEmpty()) return CreateResult.Success
        return try {
            manualIds.forEach { mid -> cloud.attachManualToPlane(orgId, mid, planeId, planeTailNumber) }
            cloud.emitOrgEvent(orgId, deviceId, "manual_attached")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't attach manuals.") }
    }

    /** Purge a manual from the Files page (admin only). Indexed references
     *  persist by design so the manual can be re-attached later. */
    suspend fun purgeManual(manualId: String): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.deleteManual(manualId)
            cloud.emitOrgEvent(orgId, deviceId, "manual_purged")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't delete manual.") }
    }

    /** Link (or, with null fields, clear) a work log's manual reference.
     *  Optimistic local update + org event; rolls back on failure. */
    fun linkWorkLogToReference(
        workLogId: String,
        referenceId: String?,
        referenceCode: String?,
        referenceTitle: String?,
        pageStart: Int?,
        pageEnd: Int?,
        sourceName: String?
    ) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.workLogs.firstOrNull { it.id == workLogId }
        _state.update { current ->
            current.copy(workLogs = current.workLogs.map { log ->
                if (log.id == workLogId) log.copy(
                    referenceId = referenceId,
                    referenceCode = referenceCode,
                    referenceTitle = referenceTitle,
                    manualPageStart = pageStart,
                    manualPageEnd = pageEnd,
                    manualSourceName = sourceName
                ) else log
            })
        }
        scope.launch {
            try {
                cloud.updateWorkLogReference(workLogId, referenceId, referenceCode, referenceTitle, pageStart, pageEnd, sourceName)
                cloud.emitOrgEvent(orgId, deviceId, "work_log_reference_linked")
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        workLogs = current.workLogs.map { log ->
                            if (log.id == workLogId && previous != null) log.copy(
                                referenceId = previous.referenceId,
                                referenceCode = previous.referenceCode,
                                referenceTitle = previous.referenceTitle,
                                manualPageStart = previous.manualPageStart,
                                manualPageEnd = previous.manualPageEnd,
                                manualSourceName = previous.manualSourceName
                            ) else log
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    /** Bulk-create work logs from the staging sheet (one org event + re-pull). */
    suspend fun createWorkLogsBulk(logs: List<NewWorkLogDraft>): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        val valid = logs.filter { it.title.trim().isNotBlank() }
        if (valid.isEmpty()) return CreateResult.Error("Add at least one work log.")
        val me = _state.value.currentUser  // paper trail
        return try {
            valid.forEach { d ->
                val wlId = cloud.createWorkLog(orgId, d.planeId, d.planeTailNumber, d.title, d.category, d.details, me?.id, me?.displayName)
                logAudit("work_log", wlId, "created", "Added work log \"${d.title.trim()}\" to ${d.planeTailNumber.uppercase()}")
            }
            cloud.emitOrgEvent(orgId, deviceId, "work_log_created")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't create work logs.") }
    }

    /** Reassign a work log. Optimistic local update + org event so every
     *  other device re-syncs. Passing nulls unassigns. */
    fun updateWorkLogAssignee(
        workLogId: String,
        assignedUserId: String?,
        assignedUserName: String?
    ) {
        val orgId = bootstrappedOrgId ?: return
        val previous = _state.value.workLogs.firstOrNull { it.id == workLogId }
        _state.update { current ->
            current.copy(
                workLogs = current.workLogs.map { log ->
                    if (log.id == workLogId) log.copy(
                        assignedUserId = assignedUserId,
                        assignedUserName = assignedUserName
                    ) else log
                }
            )
        }
        scope.launch {
            try {
                cloud.updateWorkLogAssignee(workLogId, assignedUserId, assignedUserName)
                cloud.emitOrgEvent(orgId, deviceId, "work_log_reassigned")
            } catch (t: Throwable) {
                // Roll back on failure.
                _state.update { current ->
                    current.copy(
                        workLogs = current.workLogs.map { log ->
                            if (log.id == workLogId && previous != null) log.copy(
                                assignedUserId = previous.assignedUserId,
                                assignedUserName = previous.assignedUserName
                            ) else log
                        },
                        error = t.message
                    )
                }
            }
        }
    }

    fun deletePartLocation(id: String) {
        val orgId = bootstrappedOrgId ?: return
        // Optimistic removal so the list updates immediately.
        _state.update { s -> s.copy(partLocations = s.partLocations.filterNot { it.id == id }) }
        scope.launch {
            runCatching {
                cloud.deletePartLocation(id)
                cloud.emitOrgEvent(orgId, deviceId, "part_location_deleted")
                pullSnapshot(orgId)
            }
        }
    }

    private suspend fun pullSnapshot(orgId: String) {
        try {
            val planes = cloud.fetchPlanes(orgId)
            val workLogs = cloud.fetchWorkLogs(orgId)
            val users = cloud.fetchUserProfiles(orgId)
            val manuals = cloud.fetchManuals(orgId)
            val squawks = cloud.fetchSquawks(orgId)
            val timeEntries = cloud.fetchTimeEntries(orgId)
            val partRequests = cloud.fetchPartRequests(orgId)
            val partLocations = runCatching { cloud.fetchPartLocations(orgId) }.getOrElse {
                // Table might not exist yet in older orgs — fail open
                // with an empty list so the rest of the pull still works.
                emptyList()
            }
            val tasks = runCatching { cloud.fetchTasks(orgId) }.getOrElse { emptyList() }
            // Reimbursements may not have the migration yet on older orgs.
            // Fail open so the rest of the snapshot loads.
            val reimbursements = runCatching { cloud.fetchReimbursements(orgId) }.getOrElse { emptyList() }
            val timeOffRequests = runCatching { cloud.fetchTimeOffRequests(orgId) }.getOrElse { emptyList() }
            val calendarEvents = runCatching { cloud.fetchCalendarEvents(orgId) }.getOrElse { emptyList() }
            val authUserId = runCatching {
                SupabaseClientProvider.client.auth.currentUserOrNull()?.id
            }.getOrNull()
            val me = users.firstOrNull { it.authUserId == authUserId }
            val next = _state.value.copy(
                planes = planes.sortedBy { p -> p.tailNumber.lowercase() },
                workLogs = workLogs.sortedByDescending { w -> w.updatedAt ?: "" },
                users = users,
                manuals = manuals,
                squawks = squawks,
                timeEntries = timeEntries,
                partRequests = partRequests,
                partLocations = partLocations.sortedByDescending { it.updatedAt ?: "" },
                tasks = tasks.sortedByDescending { it.updatedAt ?: "" },
                reimbursements = reimbursements.sortedByDescending { it.createdAt ?: "" },
                timeOffRequests = timeOffRequests.sortedByDescending { it.createdAt ?: "" },
                calendarEvents = calendarEvents.sortedBy { it.startDate },
                currentUser = me,
                loading = false,
                error = null
            )
            _state.value = next
            // Persist the newly pulled snapshot so the next cold start
            // can render before the network is available.
            OfflineCache.save(orgId, next)
            // Fire a local notification if the current user just picked
            // up a new assignment. Notifier tracks IDs internally so we
            // don't re-ping on every sync.
            com.hangarflow.app.AssignmentNotifier.onSnapshot(
                currentUserId = me?.id,
                workLogs = next.workLogs
            )
        } catch (t: Throwable) {
            // Network failure: keep whatever (cached) data is on screen,
            // just mark the error so Settings can show it. Don't clear.
            _state.update { it.copy(loading = false, error = t.message) }
        }
    }

    private fun startRealtime(orgId: String) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            var retryDelay = 5000L
            while (true) {
                try {
                    cloud.orgEventFlow(orgId, ownDevice = deviceId).collect {
                        retryDelay = 5000L
                        pullSnapshot(orgId)
                    }
                } catch (_: Throwable) {
                    // Socket died — wait then reconnect. Exponential
                    // backoff capped at 60s so we don't hammer the
                    // server but still recover reasonably fast.
                    _state.update { it.copy(error = "Sync disconnected — retrying…") }
                    kotlinx.coroutines.delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
                    // Clear error on retry attempt
                    _state.update { it.copy(error = null) }
                }
            }
        }
    }
}

/**
 * In-progress shift state for the 4-phase clock cycle:
 * Idle → Working → OnLunch → Working → (clock out records entry).
 *
 * `lunchStartedAt` is non-null while the tech is on lunch. Each time
 * they come back from lunch we close the bracket and accumulate the
 * minutes into `lunchMinutesAccrued`, then clear `lunchStartedAt`.
 * `lunchTaken` flips true after the first lunch ends — that's how the
 * UI knows to show "Clock Out" instead of "Lunch Out" on the next tap.
 */
data class ActiveShift(
    val userId: String?,
    val userName: String,
    val startedAt: Instant,
    val lunchStartedAt: Instant? = null,
    val lunchMinutesAccrued: Int = 0,
    val lunchTaken: Boolean = false
) {
    val phase: ShiftPhase
        get() = when {
            lunchStartedAt != null -> ShiftPhase.OnLunch
            else -> ShiftPhase.Working
        }
}

enum class ShiftPhase { Working, OnLunch }

/**
 * Live minutes worked so far in this shift. Subtracts any completed
 * lunches AND the currently-running lunch bracket, so the Today tile
 * stops ticking the moment the user hits "Lunch Out" and resumes on
 * "Lunch In".
 */
fun ActiveShift.liveWorkedMinutes(now: Instant = Instant.now()): Int {
    val totalMin = java.time.temporal.ChronoUnit.MINUTES
        .between(startedAt, now).toInt().coerceAtLeast(0)
    val tailLunch = lunchStartedAt?.let {
        java.time.temporal.ChronoUnit.MINUTES.between(it, now).toInt().coerceAtLeast(0)
    } ?: 0
    return (totalMin - lunchMinutesAccrued - tailLunch).coerceAtLeast(0)
}

private fun formatLunch(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@kotlinx.serialization.Serializable
data class ShopState(
    val planes: List<HFPlane>,
    val workLogs: List<HFWorkLog>,
    val users: List<HFUserProfile>,
    val manuals: List<HFManual>,
    val squawks: List<HFSquawk>,
    val timeEntries: List<HFTimeEntry>,
    val partRequests: List<HFPartRequest>,
    val partLocations: List<HFPartLocation>,
    val tasks: List<HFTask> = emptyList(),
    val reimbursements: List<com.hangarflow.app.data.model.HFReimbursement> = emptyList(),
    val timeOffRequests: List<com.hangarflow.app.data.model.HFTimeOffRequest> = emptyList(),
    val calendarEvents: List<com.hangarflow.app.data.model.HFCalendarEvent> = emptyList(),
    val currentUser: HFUserProfile?,
    val loading: Boolean,
    val error: String?
) {
    companion object {
        fun empty() = ShopState(
            planes = emptyList(),
            workLogs = emptyList(),
            users = emptyList(),
            manuals = emptyList(),
            squawks = emptyList(),
            timeEntries = emptyList(),
            partRequests = emptyList(),
            partLocations = emptyList(),
            tasks = emptyList(),
            reimbursements = emptyList(),
            timeOffRequests = emptyList(),
            calendarEvents = emptyList(),
            currentUser = null,
            loading = false,
            error = null
        )
    }
}
