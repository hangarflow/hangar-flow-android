package com.hangarflow.app.data.cloud

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Starting a walkthrough on Windows.
 *
 * The deck is built by the `procedure-guide` edge function, which reads THIS
 * shop's manuals and refuses to invent. Everything safety-relevant happens
 * there; this file's whole job is to ask, then store what came back without
 * softening it.
 *
 * Two of those refusals have to survive the port, because they are invisible if
 * you only read the happy path:
 *
 *  - **What the grounding gates removed is told to the mechanic**, and is
 *    FROZEN into the run's `gaps`. A walkthrough that quietly lost three steps
 *    reads exactly like a short job.
 *  - **No deck rather than an empty one.** An empty walkthrough reads as
 *    "there is nothing to this job", which is worse than being told the
 *    manuals cannot answer.
 *
 * PORTED VERBATIM from the Windows client, and it has to stay that way. The two
 * codebases share no module, so the only thing keeping a Mac, a Windows machine
 * and a tablet agreeing about an aeroplane is that these files say the same
 * thing. Change one, change the other.
 */
@Serializable
data class HFProcedureAnswer(
    val task_title: String? = null,
    val steps: List<HFProcedureStep>? = null,
    val tools: List<String>? = null,
    val cautions: List<String>? = null,
    val gaps: List<String>? = null,
    val grounded_on: List<HFGroundedSection>? = null,
    val estimate_minutes: Int? = null,
    val estimate_sample_size: Int? = null,
    /** Steps the server dropped, and why. Counted, never hidden. */
    val uncited_steps_removed: Int? = null,
    val unresolved_citations_removed: Int? = null,
    val ungrounded_spec_steps_removed: Int? = null,
    val stripped_specs: List<String>? = null,
    val empty_library: Boolean? = null,
    val error: String? = null,
)

object ProcedureGuide {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val httpClient = HttpClient()

    @Serializable
    private data class Ask(
        val question: String = "",
        val work_log_id: String? = null,
        val plane_id: String? = null,
        val aircraft_type: String? = null,
        val language: String? = null,
    )

    /**
     * Raw Ktor rather than `functions.invoke`, matching ManualImporter and
     * ManualCache: the SDK attaches its own JWT, and when that has gone stale
     * the function answers 401 while the SDK's error says nothing useful. A
     * refresh plus an explicit header turns "it didn't work" into "sign back
     * in", which is a thing a mechanic can act on.
     */
    suspend fun ask(
        workLogId: String?,
        planeId: String?,
        question: String = "",
        aircraftType: String? = null,
    ): HFProcedureAnswer = try {
        val client = SupabaseClientProvider.client
        runCatching { client.auth.refreshCurrentSession() }
        val token = client.auth.currentAccessTokenOrNull()
        if (token == null) {
            HFProcedureAnswer(
                error = "Session expired — sign out and sign back in to use the walkthrough."
            )
        } else {
            val resp = httpClient.post("${SupabaseConfig.URL}/functions/v1/procedure-guide") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        Ask.serializer(),
                        Ask(question, workLogId, planeId, aircraftType, "en"),
                    )
                )
            }
            val body = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                HFProcedureAnswer(
                    error = if (resp.status.value == 401)
                        "Session expired — sign out and sign back in to use the walkthrough."
                    else "The walkthrough service answered ${resp.status.value}: ${body.take(160)}"
                )
            } else {
                json.decodeFromString<HFProcedureAnswer>(body)
            }
        }
    } catch (t: Throwable) {
        HFProcedureAnswer(error = t.message?.take(240) ?: "Couldn't reach the walkthrough service.")
    }
}

object GuidedRunStarter {

    @Serializable
    private data class RunInsert(
        val org_id: String,
        val work_log_id: String,
        val plane_id: String? = null,
        val task_title: String,
        val steps: List<HFProcedureStep>,
        val tools: List<String>,
        val cautions: List<String>,
        val gaps: List<String>,
        val estimate_minutes: Int? = null,
        val estimate_sample_size: Int? = null,
        val grounded_on: List<HFGroundedSection>,
        val started_by_user_id: String? = null,
        val started_by_name: String? = null,
    )

    @Serializable
    private data class StartedRow(val id: String)

    /**
     * Build a deck for this work log and store it.
     *
     * Returns the run id, or a sentence explaining why there is no deck. The
     * sentence is not a failure message in the usual sense — "your manuals
     * don't have enough on this task" is a true and useful answer, and dressing
     * it up as an error teaches people to retry rather than to import the AMM.
     */
    suspend fun start(
        orgId: String,
        workLogId: String,
        planeId: String?,
        title: String,
        actorId: String?,
        actorName: String?,
    ): Pair<String?, String?> {
        val a = ProcedureGuide.ask(workLogId = workLogId, planeId = planeId)
        a.error?.let { return null to it }

        val steps = a.steps ?: emptyList()

        // What the grounding gates took out, said plainly.
        val removed = buildList {
            (a.uncited_steps_removed ?: 0).takeIf { it > 0 }
                ?.let { add("$it cited no page at all") }
            (a.unresolved_citations_removed ?: 0).takeIf { it > 0 }
                ?.let { add("$it cited a page that isn't in your manuals") }
            (a.ungrounded_spec_steps_removed ?: 0).takeIf { it > 0 }?.let { n ->
                val specs = (a.stripped_specs ?: emptyList()).take(4).joinToString(", ")
                add(
                    if (specs.isEmpty()) "$n stated a figure your manuals don't contain"
                    else "$n stated figures your manuals don't contain ($specs)"
                )
            }
        }
        val removedNote = removed.takeIf { it.isNotEmpty() }
            ?.let { "Steps removed before you saw them: ${it.joinToString("; ")}." }

        if (steps.isEmpty()) {
            return null to if (a.empty_library == true)
                "That manual isn't in your library yet, so there's nothing to walk through. " +
                    "Import the AMM for this aircraft and try again."
            else "Your manuals don't have enough on this task to build a walkthrough."
        }

        return try {
            val rows = SupabaseClientProvider.client.postgrest.from("hf_guided_runs").insert(
                RunInsert(
                    org_id = orgId, work_log_id = workLogId, plane_id = planeId,
                    task_title = a.task_title ?: title,
                    steps = steps,
                    tools = a.tools ?: emptyList(),
                    cautions = a.cautions ?: emptyList(),
                    // Folded into the gaps the deck shows, so it is FROZEN with
                    // the run. The deck is evidence of what the tech was shown,
                    // and "three steps were dropped" is part of that.
                    gaps = (a.gaps ?: emptyList()) + listOfNotNull(removedNote),
                    estimate_minutes = a.estimate_minutes,
                    estimate_sample_size = a.estimate_sample_size,
                    // Pinned with the deck. Re-searching later could return
                    // different pages, and then an answer would be about pages
                    // the steps on screen do not cite.
                    grounded_on = a.grounded_on ?: emptyList(),
                    started_by_user_id = actorId, started_by_name = actorName,
                )
            ) { select() }.decodeList<StartedRow>()
            val id = rows.firstOrNull()?.id
            if (id == null) null to "The walkthrough didn't save. Try again."
            else id to null
        } catch (t: Throwable) {
            null to (t.message?.take(240) ?: "Unknown error")
        }
    }
}
