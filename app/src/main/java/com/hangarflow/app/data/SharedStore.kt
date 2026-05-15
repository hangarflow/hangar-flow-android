package com.hangarflow.app.data

import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import com.hangarflow.app.data.cloud.TimeEntryService
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
     * End the active shift. Total shift minutes minus any lunch minutes
     * become the entry. If the tech somehow clocks out while still on
     * lunch we close the lunch bracket first so the math stays honest.
     */
    fun clockOut() {
        val shift = _activeShift.value ?: return
        val orgId = bootstrappedOrgId ?: return
        val end = Instant.now()
        val totalMin = ChronoUnit.MINUTES.between(shift.startedAt, end).toInt().coerceAtLeast(1)
        val tailLunch = shift.lunchStartedAt?.let {
            ChronoUnit.MINUTES.between(it, end).toInt().coerceAtLeast(0)
        } ?: 0
        val totalLunch = shift.lunchMinutesAccrued + tailLunch
        val minutes = (totalMin - totalLunch).coerceAtLeast(1)
        _activeShift.value = null
        ShiftPersistence.clear()
        scope.launch {
            try {
                val notes = if (totalLunch > 0) "Lunch: ${formatLunch(totalLunch)}" else ""
                timeEntryService.createTimeEntry(
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
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Failed to save time entry.") }
            }
        }
    }

    // -------- Admin create flows --------

    /** Result wrapper so the sheet can show success / error feedback. */
    sealed class CreateResult {
        object Success : CreateResult()
        data class Error(val message: String) : CreateResult()
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
        return try {
            cloud.createWorkLog(orgId, planeId, planeTailNumber, title, category, details)
            cloud.emitOrgEvent(orgId, deviceId, "work_log_created")
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
        notes: String
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
                    updatedByUserName = me?.displayName ?: "Tech"
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
                    updatedByUserName = me?.displayName ?: "Tech"
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

    suspend fun updatePlane(
        planeId: String, tailNumber: String, displayName: String,
        outlineHex: String, arrivalDate: String?, deadlineDate: String?
    ): CreateResult {
        val orgId = bootstrappedOrgId ?: return CreateResult.Error("No org loaded.")
        return try {
            cloud.updatePlane(planeId, tailNumber, displayName, outlineHex, arrivalDate, deadlineDate)
            cloud.emitOrgEvent(orgId, deviceId, "plane_updated")
            pullSnapshot(orgId)
            CreateResult.Success
        } catch (t: Throwable) { CreateResult.Error(t.message ?: "Couldn't update plane.") }
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
            currentUser = null,
            loading = false,
            error = null
        )
    }
}
