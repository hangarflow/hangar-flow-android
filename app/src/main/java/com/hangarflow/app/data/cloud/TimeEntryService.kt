package com.hangarflow.app.data.cloud

import com.hangarflow.app.data.model.HFTimeEntry
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

/**
 * Writes `hf_time_entries` rows from the Android tablet. Each clock-out
 * creates exactly one row with the minutes worked between clock-in and
 * clock-out. Insert emits the usual `hf_org_events` row so Mac + iPhone
 * see the entry through Realtime.
 */
class TimeEntryService {
    private val client get() = SupabaseClientProvider.client

    @Serializable
    private data class NewTimeEntryRow(
        val id: String,
        val org_id: String,
        val user_id: String?,
        val user_name: String,
        val plane_id: String?,
        val plane_tail_number: String?,
        val linked_work_log_id: String?,
        val linked_task_id: String?,
        val linked_squawk_id: String?,
        val entry_date: String,
        val minutes_worked: Int,
        val notes: String
    )

    suspend fun createTimeEntry(
        orgId: String,
        userId: String?,
        userName: String,
        planeId: String?,
        planeTailNumber: String?,
        entryDateIso: String,
        minutesWorked: Int,
        notes: String = "",
        linkedWorkLogId: String? = null,
        linkedTaskId: String? = null,
        linkedSquawkId: String? = null
    ): HFTimeEntry {
        val id = java.util.UUID.randomUUID().toString()
        val row = NewTimeEntryRow(
            id = id,
            org_id = orgId,
            user_id = userId,
            user_name = userName,
            plane_id = planeId,
            plane_tail_number = planeTailNumber,
            linked_work_log_id = linkedWorkLogId,
            linked_task_id = linkedTaskId,
            linked_squawk_id = linkedSquawkId,
            entry_date = entryDateIso,
            minutes_worked = minutesWorked,
            notes = notes
        )
        client.postgrest.from("hf_time_entries").insert(row)
        return HFTimeEntry(
            id = id,
            orgId = orgId,
            userId = userId,
            userName = userName,
            planeId = planeId,
            planeTailNumber = planeTailNumber,
            linkedWorkLogId = linkedWorkLogId,
            linkedTaskId = linkedTaskId,
            linkedSquawkId = linkedSquawkId,
            entryDate = entryDateIso,
            minutesWorked = minutesWorked,
            notes = notes
        )
    }
}
