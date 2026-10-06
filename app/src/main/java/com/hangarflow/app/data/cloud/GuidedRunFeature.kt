package com.hangarflow.app.data.cloud

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

/**
 * The AI walkthrough and the fault-isolation tree, on Windows.
 *
 * Ports of the Apple `HFGuidedRun.swift` and `HFTroubleshoot.swift`. Both decks
 * are generated SERVER-side by an edge function that reads this shop's own
 * manuals; nothing here asks a model anything. The client's job is to show what
 * came back, record what the mechanic did about it, and never quietly lose
 * either.
 *
 * Two properties are carried over deliberately because they are what makes the
 * feature safe rather than merely clever:
 *
 *  - **The steps are frozen.** Once a run exists its `steps` are a stored
 *    column, not something regenerated on each open. A walkthrough that
 *    silently rewrites itself between two reads of the same job is not a
 *    record of anything.
 *  - **Citations are resolved on the server** against the sections it actually
 *    sent — never a page number a model produced. That is the difference
 *    between opening the right page and printing a confident wrong one beside
 *    a correct step.
 *
 * PORTED VERBATIM from the Windows client, and it has to stay that way. The two
 * codebases share no module, so the only thing keeping a Mac, a Windows machine
 * and a tablet agreeing about an aeroplane is that these files say the same
 * thing. Change one, change the other.
 */

@Serializable
data class HFProcedureStep(
    val n: Int? = null,
    val what: String = "",
    val citation: String? = null,
    val manual_name: String? = null,
    val page: Int? = null,
    val reference_id: String? = null,
    /**
     * The manual's own words for that section, as the model received them.
     * Shown beside the page on purpose: where the extraction is truncated the
     * mechanic can see both what the AI had and what the page really says.
     */
    val extract: String? = null,
    val extract_truncated: Boolean? = null,
)

@Serializable
data class HFGroundedSection(
    val reference_id: String? = null,
    val reference_code: String? = null,
    val manual: String? = null,
    val page: Int? = null,
)

data class HFGuidedRun(
    val id: String,
    val workLogId: String? = null,
    val planeId: String? = null,
    val taskTitle: String = "",
    val steps: List<HFProcedureStep> = emptyList(),
    val tools: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    /**
     * The manual sections this deck was built from, pinned. Roger answers from
     * these and the steps and has no path to anything else.
     */
    val groundedOn: List<HFGroundedSection> = emptyList(),
    val estimateMinutes: Int? = null,
    val estimateSampleSize: Int? = null,
    val doneSteps: Set<Int> = emptySet(),
    val startedAt: String? = null,
    val startedByName: String? = null,
    val completedAt: String? = null,
) {
    val isComplete: Boolean get() = !completedAt.isNullOrBlank()
    val remaining: Int get() = maxOf(0, steps.size - doneSteps.size)
    val progress: Double
        get() = if (steps.isEmpty()) 0.0 else doneSteps.size.toDouble() / steps.size

    /** The first step not yet ticked — where the deck should open. */
    val currentIndex: Int?
        get() = steps.indices.firstOrNull { (steps[it].n ?: (it + 1)) !in doneSteps }
}

@Serializable
private data class GuidedRunRow(
    val id: String,
    val work_log_id: String? = null,
    val plane_id: String? = null,
    val task_title: String = "",
    val steps: List<HFProcedureStep>? = null,
    val tools: List<String>? = null,
    val cautions: List<String>? = null,
    val gaps: List<String>? = null,
    val grounded_on: List<HFGroundedSection>? = null,
    val estimate_minutes: Int? = null,
    val estimate_sample_size: Int? = null,
    val done_steps: List<Int>? = null,
    val started_at: String? = null,
    val started_by_name: String? = null,
    val completed_at: String? = null,
    val abandoned_at: String? = null,
) {
    fun toModel() = HFGuidedRun(
        id = id, workLogId = work_log_id, planeId = plane_id, taskTitle = task_title,
        steps = steps ?: emptyList(), tools = tools ?: emptyList(),
        cautions = cautions ?: emptyList(), gaps = gaps ?: emptyList(),
        groundedOn = grounded_on ?: emptyList(),
        estimateMinutes = estimate_minutes, estimateSampleSize = estimate_sample_size,
        doneSteps = (done_steps ?: emptyList()).toSet(),
        startedAt = started_at, startedByName = started_by_name, completedAt = completed_at,
    )

    val isLive: Boolean get() = abandoned_at.isNullOrBlank()
}

@Serializable
private data class RunIdRow(val id: String)

object GuidedRunCloud {

    private val client get() = SupabaseClientProvider.client

    suspend fun fetch(orgId: String): List<HFGuidedRun> =
        client.postgrest.from("hf_guided_runs").select {
            filter { eq("org_id", orgId) }
        }.decodeList<GuidedRunRow>().filter { it.isLive }.map { it.toModel() }

    @Serializable
    private data class DonePatch(
        val done_steps: List<Int>,
        val completed_at: String? = null,
    )

    /**
     * Tick a step, or untick it.
     *
     * Swiping a step away has to MEAN it stuck. RLS refuses by matching
     * nothing, so without the read-back a ticked step would reappear on the
     * next refresh with no explanation and the mechanic would redo it.
     */
    suspend fun setStepDone(
        run: HFGuidedRun,
        step: Int,
        done: Boolean,
        nowIso: String,
    ): LogbookError {
        val next = run.doneSteps.toMutableSet().apply { if (done) add(step) else remove(step) }
        val all = run.steps.isNotEmpty() && next.size >= run.steps.size
        return try {
            val touched = client.postgrest.from("hf_guided_runs").update(
                DonePatch(next.sorted(), if (all) nowIso else null)
            ) { select(); filter { eq("id", run.id) } }.decodeList<RunIdRow>()
            if (touched.isEmpty())
                "That step didn't save — this walkthrough is no longer on file. Start it again."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }

    @Serializable
    private data class AbandonPatch(val abandoned_at: String)

    suspend fun abandon(run: HFGuidedRun, nowIso: String): LogbookError = try {
        val touched = client.postgrest.from("hf_guided_runs").update(AbandonPatch(nowIso)) {
            select(); filter { eq("id", run.id) }
        }.decodeList<RunIdRow>()
        if (touched.isEmpty()) "That walkthrough wasn't closed out. Refresh and try again."
        else null
    } catch (t: Throwable) {
        t.message?.take(240) ?: "Unknown error"
    }
}

// ── Fault isolation ───────────────────────────────────────────────────

enum class HFCauseOutcome(val raw: String) {
    UNTESTED("untested"), RULED_OUT("ruled_out"), CONFIRMED("confirmed");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: UNTESTED
    }
}

@Serializable
data class HFTroubleCause(
    val n: Int? = null,
    val cause: String = "",
    val check: String? = null,
    val citation: String? = null,
    val manual_name: String? = null,
    val page: Int? = null,
    val reference_id: String? = null,
    val extract: String? = null,
    val extract_truncated: Boolean? = null,
    val outcome: String? = null,
    val note: String? = null,
    val by: String? = null,
) {
    val state: HFCauseOutcome get() = HFCauseOutcome.from(outcome)
}

data class HFTroubleshootRun(
    val id: String,
    val planeId: String? = null,
    val squawkId: String? = null,
    val symptom: String = "",
    val manualSymptom: String? = null,
    val causes: List<HFTroubleCause> = emptyList(),
    val cautions: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val groundedOn: List<HFGroundedSection> = emptyList(),
    val foundCause: Int? = null,
    val closedAt: String? = null,
    val closedReason: String? = null,
    val startedAt: String? = null,
    val startedByName: String? = null,
) {
    val isClosed: Boolean get() = !closedAt.isNullOrBlank()
    val ruledOut: Int get() = causes.count { it.state == HFCauseOutcome.RULED_OUT }
    val confirmed: HFTroubleCause? get() = causes.firstOrNull { it.state == HFCauseOutcome.CONFIRMED }

    /**
     * The line that goes in the logbook, and the line the next mechanic wants.
     * "We checked X and Y, both good" is the thing that never gets written
     * down, so it is composed here rather than left to a tired human at 5pm.
     */
    val eliminationSummary: String?
        get() {
            val out = causes.filter { it.state == HFCauseOutcome.RULED_OUT }.map { it.cause }
            if (out.isEmpty()) return null
            val hit = confirmed
            return if (hit != null) "Ruled out: ${out.joinToString("; ")}. Found: ${hit.cause}."
            else "Ruled out: ${out.joinToString("; ")}."
        }
}

@Serializable
private data class TroubleRow(
    val id: String,
    val plane_id: String? = null,
    val squawk_id: String? = null,
    val symptom: String = "",
    val manual_symptom: String? = null,
    val causes: List<HFTroubleCause>? = null,
    val cautions: List<String>? = null,
    val gaps: List<String>? = null,
    val grounded_on: List<HFGroundedSection>? = null,
    val found_cause: Int? = null,
    val closed_at: String? = null,
    val closed_reason: String? = null,
    val started_at: String? = null,
    val started_by_name: String? = null,
) {
    fun toModel() = HFTroubleshootRun(
        id = id, planeId = plane_id, squawkId = squawk_id, symptom = symptom,
        manualSymptom = manual_symptom, causes = causes ?: emptyList(),
        cautions = cautions ?: emptyList(), gaps = gaps ?: emptyList(),
        groundedOn = grounded_on ?: emptyList(), foundCause = found_cause,
        closedAt = closed_at, closedReason = closed_reason,
        startedAt = started_at, startedByName = started_by_name,
    )
}

object TroubleshootCloud {

    private val client get() = SupabaseClientProvider.client

    suspend fun fetch(orgId: String): List<HFTroubleshootRun> =
        client.postgrest.from("hf_troubleshoot_runs").select {
            filter { eq("org_id", orgId) }
        }.decodeList<TroubleRow>().map { it.toModel() }

    @Serializable
    private data class OutcomePatch(
        val causes: List<HFTroubleCause>,
        val found_cause: Int? = null,
        val closed_at: String? = null,
    )

    /**
     * Record what a check actually found.
     *
     * What was RULED OUT is the whole value of this record to the next
     * mechanic, so a write that matched no rows must not look like one that
     * landed.
     */
    suspend fun setOutcome(
        run: HFTroubleshootRun,
        causeNumber: Int,
        outcome: HFCauseOutcome,
        note: String?,
        by: String?,
        nowIso: String,
    ): LogbookError {
        val causes = run.causes.map {
            if (it.n == causeNumber) it.copy(
                outcome = outcome.raw,
                note = note?.trim()?.takeIf { s -> s.isNotEmpty() } ?: it.note,
                by = by ?: it.by,
            ) else it
        }
        val confirmed = outcome == HFCauseOutcome.CONFIRMED
        return try {
            val touched = client.postgrest.from("hf_troubleshoot_runs").update(
                OutcomePatch(
                    causes = causes,
                    found_cause = if (confirmed) causeNumber else null,
                    closed_at = if (confirmed) nowIso else null,
                )
            ) { select(); filter { eq("id", run.id) } }.decodeList<RunIdRow>()
            if (touched.isEmpty())
                "That didn't save — this troubleshooting record is no longer on file. " +
                    "Start it again."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }

    @Serializable
    private data class ClosePatch(val closed_at: String, val closed_reason: String)

    /**
     * Stop without finding it. The reason is required by a CHECK constraint,
     * because "we stopped looking" with no reason is what makes the next person
     * start from scratch.
     */
    suspend fun close(run: HFTroubleshootRun, reason: String, nowIso: String): LogbookError {
        val why = reason.trim()
        if (why.isEmpty()) return "Say why you're stopping."
        return try {
            val touched = client.postgrest.from("hf_troubleshoot_runs").update(
                ClosePatch(nowIso, why)
            ) { select(); filter { eq("id", run.id) } }.decodeList<RunIdRow>()
            if (touched.isEmpty()) "That didn't save — the record is no longer on file."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }
}
