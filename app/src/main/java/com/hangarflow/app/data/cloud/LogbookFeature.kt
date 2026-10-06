package com.hangarflow.app.data.cloud

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The aircraft's permanent maintenance record, on Windows.
 *
 * A port of the Apple `HFLogbookFeature.swift`, deliberately field-for-field:
 * the same table, the same enums, the same refusals. Divergence here is not a
 * style question — a rule that holds on a Mac and not on the Windows client is
 * not a rule, and this is the one feature where the database is enforcing
 * 14 CFR 43.
 *
 * Windows matters most for this: it is the machine that imports the due list,
 * so it is where the work is entered and where the entry closing that work
 * belongs.
 *
 * Every mutating call reads its row back. Postgres RLS refuses by matching
 * ZERO ROWS rather than raising, so `.update(...).eq(...)` succeeds identically
 * whether it changed a row or nothing at all — the bug class that has already
 * cost this project an invoice and a manual. `select()` plus a count is the
 * only proof.
 *
 * PORTED VERBATIM from the Windows client, and it has to stay that way. The two
 * codebases share no module, so the only thing keeping a Mac, a Windows machine
 * and a tablet agreeing about an aeroplane is that these files say the same
 * thing. Change one, change the other.
 */

// ── Which book the entry goes in ──────────────────────────────────────

enum class HFLogSection(val raw: String, val label: String) {
    AIRFRAME("airframe", "Airframe"),
    ENGINE1("engine1", "Engine 1"),
    ENGINE2("engine2", "Engine 2"),
    ENGINE3("engine3", "Engine 3"),
    PROP1("prop1", "Propeller 1"),
    PROP2("prop2", "Propeller 2"),
    APU("apu", "APU");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: AIRFRAME
    }
}

// ── What kind of entry it is ──────────────────────────────────────────

enum class HFLogEntryType(val raw: String, val label: String) {
    MAINTENANCE("maintenance", "Maintenance"),
    PREVENTIVE("preventive", "Preventive maintenance"),
    ANNUAL("annual", "Annual inspection"),
    ONE_HUNDRED_HOUR("100_hour", "100-hour inspection"),
    PROGRESSIVE("progressive", "Progressive inspection"),
    AD_COMPLIANCE("ad_compliance", "AD compliance"),
    MAJOR_REPAIR("major_repair", "Major repair"),
    MAJOR_ALTERATION("major_alteration", "Major alteration"),
    ALTIMETER_STATIC("altimeter_static", "Altimeter / static system"),
    TRANSPONDER("transponder", "Transponder"),
    ELT("elt", "ELT"),
    CUSTOM("custom", "Custom");

    /** An inspection owes an airworthiness finding; ordinary maintenance does not. */
    val isInspection: Boolean
        get() = this == ANNUAL || this == ONE_HUNDRED_HOUR || this == PROGRESSIVE

    val needsAirworthinessFinding: Boolean get() = isInspection

    /**
     * An annual or a progressive must be signed by an IA or a repair station.
     * An A&P alone cannot approve either for return to service.
     */
    val needsIA: Boolean get() = this == ANNUAL || this == PROGRESSIVE

    /** A major repair or alteration also owes FAA Form 337. */
    val needs337: Boolean get() = this == MAJOR_REPAIR || this == MAJOR_ALTERATION

    val isCustom: Boolean get() = this == CUSTOM

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: MAINTENANCE
    }
}

enum class HFLogAttachmentKind(val raw: String, val label: String) {
    RELEASE_CERT("release_cert", "Release certificate (8130-3 / Form 1 / CoC)"),
    WORK_ORDER("work_order", "Work order"),
    PHOTO("photo", "Photo"),
    TEST_RESULT("test_result", "Test result"),
    FORM_337("form_337", "FAA Form 337"),
    INVOICE("invoice", "Invoice"),
    OTHER("other", "Other document");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

// ── The entry ─────────────────────────────────────────────────────────

data class HFLogbookEntry(
    val id: String,
    val planeId: String,
    val workOrderId: String? = null,
    val logSection: HFLogSection = HFLogSection.AIRFRAME,
    val entryType: HFLogEntryType = HFLogEntryType.MAINTENANCE,
    val customTypeLabel: String? = null,
    val entryDate: String,
    /** What was wrong. Absent for scheduled work — forcing it teaches people to type "N/A". */
    val reportedDiscrepancy: String? = null,
    /**
     * Who did the work when that is not the person signing. 14 CFR 43.9(a)(3)
     * requires it, and it is the normal case: a tech does the job, an A&P or an
     * IA inspects it and approves the aircraft for return to service.
     */
    val performedByName: String? = null,
    val aircraftTotalTime: Double? = null,
    val tachTime: Double? = null,
    val hobbsTime: Double? = null,
    val body: String,
    val isAirworthy: Boolean? = null,
    val discrepancies: String? = null,
    val sourceWorkLogIds: List<String> = emptyList(),
    val draftedByAI: Boolean = false,
    val signedAt: String? = null,
    val signedByName: String? = null,
    val signerCertNumber: String? = null,
    val signerCertType: String? = null,
    val repairStationNumber: String? = null,
    val supersedesEntryId: String? = null,
    val voidReason: String? = null,
    val createdByUserName: String? = null,
) {
    val isSigned: Boolean get() = !signedAt.isNullOrBlank()
    val isVoid: Boolean get() = !voidReason.isNullOrBlank()

    val statusLabel: String
        get() = when {
            isVoid -> "Void"
            isSigned -> "Signed"
            else -> "Draft"
        }

    val typeLabel: String
        get() = if (entryType.isCustom && !customTypeLabel.isNullOrBlank()) customTypeLabel
        else entryType.label

    /**
     * The entry as it goes in the book: what was wrong, then what was done
     * about it. An owner or an auditor reads it that way. When there is no
     * discrepancy — an oil change is not a complaint — it is just the entry.
     */
    val renderedBody: String
        get() {
            val d = reportedDiscrepancy?.trim()
            return if (d.isNullOrEmpty()) body else "Discrepancy: $d\n\nCorrective action: $body"
        }

    /** The signature block, or null while it is still a draft. */
    val signatureBlock: String?
        get() {
            if (!isSigned) return null
            val who = signedByName ?: return null
            val cert = listOfNotNull(signerCertType, signerCertNumber)
                .filter { it.isNotBlank() }.joinToString(" ")
            val station = repairStationNumber?.takeIf { it.isNotBlank() }
                ?.let { "\nRepair Station $it" } ?: ""
            return "$who — $cert$station\nSigned ${signedAt ?: ""}"
        }
}

@Serializable
private data class LogbookRow(
    val id: String,
    val org_id: String,
    val plane_id: String,
    val work_order_id: String? = null,
    val log_section: String? = null,
    val entry_type: String? = null,
    val custom_type_label: String? = null,
    val entry_date: String,
    val reported_discrepancy: String? = null,
    val performed_by_name: String? = null,
    val aircraft_total_time: Double? = null,
    val tach_time: Double? = null,
    val hobbs_time: Double? = null,
    val body: String = "",
    val is_airworthy: Boolean? = null,
    val discrepancies: String? = null,
    val source_work_log_ids: List<String>? = null,
    val drafted_by_ai: Boolean? = null,
    val signed_at: String? = null,
    val signed_by_name: String? = null,
    val signer_cert_number: String? = null,
    val signer_cert_type: String? = null,
    val repair_station_number: String? = null,
    val supersedes_entry_id: String? = null,
    val void_reason: String? = null,
    val created_by_user_name: String? = null,
) {
    fun toModel() = HFLogbookEntry(
        id = id, planeId = plane_id, workOrderId = work_order_id,
        logSection = HFLogSection.from(log_section),
        entryType = HFLogEntryType.from(entry_type),
        customTypeLabel = custom_type_label, entryDate = entry_date,
        reportedDiscrepancy = reported_discrepancy, performedByName = performed_by_name,
        aircraftTotalTime = aircraft_total_time, tachTime = tach_time, hobbsTime = hobbs_time,
        body = body, isAirworthy = is_airworthy, discrepancies = discrepancies,
        sourceWorkLogIds = source_work_log_ids ?: emptyList(),
        draftedByAI = drafted_by_ai ?: false,
        signedAt = signed_at, signedByName = signed_by_name,
        signerCertNumber = signer_cert_number, signerCertType = signer_cert_type,
        repairStationNumber = repair_station_number,
        supersedesEntryId = supersedes_entry_id, voidReason = void_reason,
        createdByUserName = created_by_user_name,
    )
}

data class HFLogAttachment(
    val id: String,
    val entryId: String,
    val filePath: String,
    val fileName: String,
    val contentType: String? = null,
    val byteSize: Long? = null,
    val kind: HFLogAttachmentKind = HFLogAttachmentKind.OTHER,
    val caption: String? = null,
    val uploadedAt: String? = null,
    val uploadedByName: String? = null,
)

@Serializable
private data class LogAttachmentRow(
    val id: String,
    val entry_id: String,
    val file_path: String,
    val file_name: String,
    val content_type: String? = null,
    val byte_size: Long? = null,
    val kind: String? = null,
    val caption: String? = null,
    val uploaded_at: String? = null,
    val uploaded_by_name: String? = null,
) {
    fun toModel() = HFLogAttachment(
        id = id, entryId = entry_id, filePath = file_path, fileName = file_name,
        contentType = content_type, byteSize = byte_size,
        kind = HFLogAttachmentKind.from(kind), caption = caption,
        uploadedAt = uploaded_at, uploadedByName = uploaded_by_name,
    )
}

@Serializable
private data class IdOnly(val id: String)

/**
 * The outcome of a write. `null` means it landed; anything else is a sentence
 * for the user that names what actually failed.
 */
typealias LogbookError = String?

object LogbookCloud {

    private val client get() = SupabaseClientProvider.client

    // ── Reading ───────────────────────────────────────────────────────

    suspend fun fetchEntries(orgId: String): List<HFLogbookEntry> =
        client.postgrest.from("hf_logbook_entries").select {
            filter { eq("org_id", orgId) }
        }.decodeList<LogbookRow>().map { it.toModel() }
            .sortedByDescending { it.entryDate }

    suspend fun fetchAttachments(orgId: String): List<HFLogAttachment> =
        client.postgrest.from("hf_logbook_attachments").select {
            filter { eq("org_id", orgId) }
        }.decodeList<LogAttachmentRow>().map { it.toModel() }

    // ── Writing ───────────────────────────────────────────────────────

    @Serializable
    private data class EntryUpsert(
        val id: String,
        val org_id: String,
        val plane_id: String,
        val work_order_id: String? = null,
        val log_section: String,
        val entry_type: String,
        val custom_type_label: String? = null,
        val entry_date: String,
        val reported_discrepancy: String? = null,
        val performed_by_name: String? = null,
        val aircraft_total_time: Double? = null,
        val tach_time: Double? = null,
        val hobbs_time: Double? = null,
        val body: String,
        val is_airworthy: Boolean? = null,
        val discrepancies: String? = null,
        val source_work_log_ids: List<String>,
        val drafted_by_ai: Boolean,
        val supersedes_entry_id: String? = null,
        val created_by_user_id: String? = null,
        val created_by_user_name: String? = null,
    )

    private fun blank(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun save(
        orgId: String,
        e: HFLogbookEntry,
        actorId: String?,
        actorName: String?,
    ): LogbookError = try {
        val touched = client.postgrest.from("hf_logbook_entries").upsert(
            EntryUpsert(
                id = e.id, org_id = orgId, plane_id = e.planeId,
                work_order_id = e.workOrderId,
                log_section = e.logSection.raw, entry_type = e.entryType.raw,
                custom_type_label = blank(e.customTypeLabel), entry_date = e.entryDate,
                reported_discrepancy = blank(e.reportedDiscrepancy),
                performed_by_name = blank(e.performedByName),
                aircraft_total_time = e.aircraftTotalTime, tach_time = e.tachTime,
                hobbs_time = e.hobbsTime, body = e.body,
                is_airworthy = e.isAirworthy, discrepancies = blank(e.discrepancies),
                source_work_log_ids = e.sourceWorkLogIds, drafted_by_ai = e.draftedByAI,
                supersedes_entry_id = e.supersedesEntryId,
                created_by_user_id = actorId, created_by_user_name = actorName,
            )
        ) { select() }.decodeList<IdOnly>()
        if (touched.isEmpty())
            "That entry didn't save. You may not have permission to write to this aircraft's log."
        else null
    } catch (t: Throwable) {
        message(t)
    }

    @Serializable
    private data class SignPatch(
        val signed_at: String,
        val signed_by_user_id: String,
        val signed_by_name: String,
        val signer_cert_number: String,
        val signer_cert_type: String,
    )

    /**
     * Sign it. The certificate comes from the signer's OWN profile and is sent
     * as-is; Postgres then checks it matches and refuses if it does not. There
     * is deliberately no field anywhere in this feature to type a certificate
     * number into, because a typed certificate number is not a signature.
     */
    suspend fun sign(
        entry: HFLogbookEntry,
        signerAuthUserId: String?,
        signerName: String,
        certNumber: String?,
        certType: String?,
        nowIso: String,
    ): LogbookError {
        if (certNumber.isNullOrBlank() || certType.isNullOrBlank())
            return "No certificate on file for $signerName. An admin adds it under Users — " +
                "14 CFR 43.9(a)(4) requires the number and the kind of certificate."
        if (signerAuthUserId.isNullOrBlank())
            return "That profile isn't linked to a sign-in yet."
        if (entry.entryType.needsIA && certType != "A&P/IA" && certType != "Repair Station")
            return "An annual or progressive must be signed by an IA or a repair station. " +
                "You hold $certType."
        return try {
            val touched = client.postgrest.from("hf_logbook_entries").update(
                SignPatch(nowIso, signerAuthUserId, signerName, certNumber, certType)
            ) { select(); filter { eq("id", entry.id) } }.decodeList<IdOnly>()
            // A signature that wrote nothing is the worst silent failure here:
            // the tech believes the aircraft was returned to service and the
            // record says otherwise. The database raises on a certificate
            // mismatch and on an open closeout blocker; this catches the other
            // case, where the row simply was not there.
            if (touched.isEmpty())
                "Nothing was signed — this entry is no longer in the log. Refresh and check it."
            else null
        } catch (t: Throwable) {
            message(t)
        }
    }

    @Serializable
    private data class VoidPatch(val void_reason: String)

    /**
     * Void a signed entry. It is not removed and cannot be: the original text
     * stays in the record, which is the entire point of a maintenance record.
     */
    suspend fun void(entry: HFLogbookEntry, reason: String): LogbookError {
        val why = reason.trim()
        if (why.isEmpty()) return "Say why it is being voided."
        return try {
            val touched = client.postgrest.from("hf_logbook_entries").update(VoidPatch(why)) {
                select(); filter { eq("id", entry.id) }
            }.decodeList<IdOnly>()
            if (touched.isEmpty())
                "That entry wasn't voided — it is no longer in the log, or you can't write to it."
            else null
        } catch (t: Throwable) {
            message(t)
        }
    }

    /** Delete a DRAFT. A signed entry is refused by the database. */
    suspend fun deleteDraft(entry: HFLogbookEntry): LogbookError {
        if (entry.isSigned) return "A signed entry can't be deleted. Void it instead."
        return try {
            // The delete policy is lead-or-admin AND unsigned. A tech deleting
            // someone else's draft matches zero rows and raises nothing, so
            // without the read-back the draft would appear to vanish and come
            // straight back on the next refresh.
            val touched = client.postgrest.from("hf_logbook_entries").delete {
                select(); filter { eq("id", entry.id) }
            }.decodeList<IdOnly>()
            if (touched.isEmpty())
                "That draft wasn't deleted — only a lead or an admin can remove one."
            else null
        } catch (t: Throwable) {
            message(t)
        }
    }

    /**
     * Turn a Postgres failure into something a mechanic can act on. The
     * database's own refusals are deliberate and worth quoting rather than
     * flattening into "something went wrong".
     */
    private fun message(t: Throwable): String {
        val raw = t.message ?: "Unknown error"
        return when {
            raw.contains("check_violation", true) || raw.contains("hf_logbook_block_signed_edit") ->
                "A signed entry can't be edited — only voided and superseded."
            raw.contains("hf_logbook_verify_signer", true) ->
                "That certificate doesn't match the one on your profile, so the entry " +
                    "wasn't signed."
            raw.contains("closeout", true) ->
                "This job still has blockers that must be cleared before anything can be " +
                    "returned to service."
            else -> raw.take(240)
        }
    }
}
