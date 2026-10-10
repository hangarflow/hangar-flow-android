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
            return null to when {
                a.empty_library == true ->
                    "That manual isn't in your library yet, so there's nothing to walk through. " +
                        "Import the AMM for this aircraft and try again."

                // The grounding gates removed EVERY step. "Not enough on this
                // task" is then a false explanation: the manuals had plenty,
                // the generated steps failed to cite it. That distinction is
                // the difference between "import the AMM" (already done) and
                // "the citations didn't line up", and the reason was already
                // computed and then thrown away — which sent a real user
                // hunting a manual that was sitting right there with 9,097
                // indexed sections.
                removedNote != null ->
                    "A walkthrough was drafted for this task but nothing survived " +
                        "the manual check, so there is nothing to show. $removedNote"

                // The function explains itself in `gaps`, and that explanation
                // was being discarded in favour of a generic line blaming the
                // manuals. "Nothing matched" and "the pages retrieved have no
                // procedure text" are different problems with different fixes,
                // and only the function knows which happened.
                !a.gaps.isNullOrEmpty() ->
                    a.gaps!!.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

                else ->
                    "Your manuals don't have enough on this task to build a walkthrough."
            }
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

/**
 * The answer from `troubleshoot-tree`.
 *
 * Mirrors the Apple decoder field for field, including the counters for what
 * the grounding gates removed. Those counters are the reason this type is not
 * just `List<HFTroubleCause>`: a tree quietly missing two causes reads as a
 * simple fault, and the mechanic has to be told.
 */
@Serializable
data class HFTroubleAnswer(
    val answerable: Boolean? = null,
    val symptom: String? = null,
    val symptom_as_manual_states_it: String? = null,
    val causes: List<HFTroubleCause>? = null,
    val cautions: List<String>? = null,
    val gaps: List<String>? = null,
    val unresolved_citations_removed: Int? = null,
    val ungrounded_spec_causes_removed: Int? = null,
    val stripped_specs: List<String>? = null,
    val grounded_on: List<HFGroundedSection>? = null,
    val sections_used: Int? = null,
    /**
     * The manual isolates this fault with a DIAGRAM, not a list. Text
     * extraction loses the arrows, so the page itself is the answer.
     */
    val is_flowchart: Boolean? = null,
    val figure_pages: List<HFGroundedSection>? = null,
    val error: String? = null,
)

/**
 * Starting a troubleshooting tree.
 *
 * This existed on Apple and nowhere else. Windows and Android had `fetch`,
 * `setOutcome` and `close` — everything needed to work a tree that already
 * existed, and no way to create one. `hf_troubleshoot_runs` held zero rows
 * across every shop, and a 395-line screen rendered a run that nothing could
 * produce.
 *
 * Deliberately the same shape as [GuidedRunStarter]: ask the function, keep
 * every refusal it hands back, freeze what came back. The two features answer
 * different questions — "what are the probable causes" versus "how do I do
 * this" — and share the rule that neither invents anything the manual does
 * not say.
 */
object TroubleshootStarter {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val httpClient = HttpClient()

    @Serializable
    private data class Ask(
        val symptom: String,
        val squawk_id: String? = null,
        val plane_id: String? = null,
        val language: String? = null,
    )

    @Serializable
    private data class TroubleInsert(
        val org_id: String,
        val plane_id: String? = null,
        val squawk_id: String? = null,
        val work_log_id: String? = null,
        val symptom: String,
        val manual_symptom: String? = null,
        val causes: List<HFTroubleCause>,
        val cautions: List<String>,
        val gaps: List<String>,
        val grounded_on: List<HFGroundedSection>,
        val started_by_user_id: String? = null,
        val started_by_name: String? = null,
    )

    @Serializable
    private data class StartedTroubleRow(val id: String)

    /** Raw Ktor for the same reason [ProcedureGuide.ask] uses it: a stale JWT
     *  answers 401, and the SDK's error for that says nothing a mechanic can
     *  act on. */
    private suspend fun ask(
        symptom: String,
        squawkId: String?,
        planeId: String?,
    ): HFTroubleAnswer = try {
        val client = SupabaseClientProvider.client
        runCatching { client.auth.refreshCurrentSession() }
        val token = client.auth.currentAccessTokenOrNull()
        if (token == null) {
            HFTroubleAnswer(
                error = "Session expired — sign out and sign back in to use troubleshooting."
            )
        } else {
            val resp = httpClient.post("${SupabaseConfig.URL}/functions/v1/troubleshoot-tree") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", SupabaseConfig.ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        Ask.serializer(),
                        Ask(symptom, squawkId, planeId, "en"),
                    )
                )
            }
            val body = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                HFTroubleAnswer(
                    error = if (resp.status.value == 401)
                        "Session expired — sign out and sign back in to use troubleshooting."
                    else "The troubleshooting service answered ${resp.status.value}: ${body.take(160)}"
                )
            } else {
                json.decodeFromString<HFTroubleAnswer>(body)
            }
        }
    } catch (t: Throwable) {
        HFTroubleAnswer(
            error = t.message?.take(240) ?: "Couldn't reach the troubleshooting service."
        )
    }

    /**
     * Read the manual's probable causes for [symptom] and freeze them.
     *
     * Returns the run id, or a sentence saying why there is no tree. As with
     * the walkthrough, "your manuals have no troubleshooting table for this"
     * is a true answer rather than an error, and must not be worded as one —
     * the fix is to import the chapter, not to press the button again.
     */
    suspend fun start(
        orgId: String,
        symptom: String,
        squawkId: String?,
        workLogId: String?,
        planeId: String?,
        actorId: String?,
        actorName: String?,
    ): Pair<String?, String?> {
        val a = ask(symptom = symptom, squawkId = squawkId, planeId = planeId)
        a.error?.takeIf { it.isNotBlank() }?.let { return null to it }

        // A flowchart is not a failure and must not be worded like one. The
        // right answer is "here is the figure" — the page carries arrows that
        // text extraction cannot.
        if (a.is_flowchart == true) {
            val pages = (a.figure_pages ?: emptyList()).mapNotNull { p ->
                p.page?.let { n -> p.manual?.let { "$it p.$n" } ?: "p. $n" }
            }
            return null to (
                "Your manual isolates this one with a flowchart, not a list of causes. " +
                    "Open the figure and work down it — " +
                    if (pages.isEmpty()) "see the fault isolation section."
                    else pages.take(3).joinToString(", ") + "."
                )
        }

        val causes = a.causes ?: emptyList()
        if (causes.isEmpty()) {
            // No tree rather than an empty one. An empty list of causes reads
            // as "there is nothing wrong", which is the opposite of true.
            return null to if ((a.sections_used ?: 0) == 0)
                "Nothing in your manuals matches that symptom. If the troubleshooting " +
                    "section for this system hasn't been imported, import it first."
            else "Your manuals don't have a troubleshooting table for this symptom."
        }

        // What the gates removed, folded into the gaps so it is frozen with the
        // run. The deck is evidence of what the mechanic was shown.
        val gaps = buildList {
            addAll(a.gaps ?: emptyList())
            val removed = buildList {
                (a.unresolved_citations_removed ?: 0).takeIf { it > 0 }
                    ?.let { add("$it cited a page that isn't in your manuals") }
                (a.ungrounded_spec_causes_removed ?: 0).takeIf { it > 0 }?.let { n ->
                    val specs = (a.stripped_specs ?: emptyList()).take(4).joinToString(", ")
                    add(
                        if (specs.isEmpty()) "$n stated a figure your manuals don't contain"
                        else "$n stated figures your manuals don't contain ($specs)"
                    )
                }
            }
            if (removed.isNotEmpty()) {
                add("Causes removed before you saw them: ${removed.joinToString("; ")}.")
            }
        }

        return try {
            val rows = SupabaseClientProvider.client.postgrest
                .from("hf_troubleshoot_runs").insert(
                    TroubleInsert(
                        org_id = orgId, plane_id = planeId,
                        squawk_id = squawkId, work_log_id = workLogId,
                        symptom = a.symptom ?: symptom,
                        // How the MANUAL words this symptom, frozen with the
                        // tree rather than recomputed on read.
                        manual_symptom = a.symptom_as_manual_states_it,
                        causes = causes,
                        cautions = a.cautions ?: emptyList(),
                        gaps = gaps,
                        // Pinned with the tree. Re-searching later could return
                        // different pages, and then an answer would cite pages
                        // the causes on screen do not.
                        grounded_on = a.grounded_on ?: emptyList(),
                        started_by_user_id = actorId, started_by_name = actorName,
                    )
                ) { select() }.decodeList<StartedTroubleRow>()
            val id = rows.firstOrNull()?.id
            // RLS refuses by returning NO ROWS, not by raising. Without this
            // check a refused insert reads as a successful one.
            if (id == null) null to "The troubleshooting record didn't save. Try again."
            else id to null
        } catch (t: Throwable) {
            null to (t.message?.take(240) ?: "Unknown error")
        }
    }
}
