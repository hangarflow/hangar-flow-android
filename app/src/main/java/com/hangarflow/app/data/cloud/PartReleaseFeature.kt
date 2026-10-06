package com.hangarflow.app.data.cloud

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * Receiving inspection: the paperwork a part arrives on, on Windows.
 *
 * A port of the Apple `HFPartReleaseFeature.swift`. The thing this feature is
 * actually for is 14 CFR 43.13 — a mechanic has to determine a part is
 * acceptable BEFORE installing it, and the determination is theirs, not the
 * vendor's and not the software's. So this records what arrived and who
 * accepted it; it never decides.
 *
 * The distinction the whole feature turns on: **not every part needs an
 * 8130-3.** Standard AN/MS/NAS hardware never has one and never will. Treating
 * a missing tag as a failure would train shops to fake them, so the exceptions
 * are first-class — and each one has to say why, which the database enforces
 * with a CHECK as well.
 *
 * PORTED VERBATIM from the Windows client, and it has to stay that way. The two
 * codebases share no module, so the only thing keeping a Mac, a Windows machine
 * and a tablet agreeing about an aeroplane is that these files say the same
 * thing. Change one, change the other.
 */

enum class HFReleaseDocType(val raw: String, val label: String, val hint: String) {
    FORM_8130(
        "8130-3", "FAA 8130-3",
        "The release for an overhauled or repaired article from a repair station.",
    ),
    EASA_FORM_1("easa_form_1", "EASA Form 1", "The European equivalent of an 8130-3."),
    DUAL_RELEASE(
        "dual_release", "Dual release (8130-3 + Form 1)",
        "Released under both FAA and EASA. Common on export.",
    ),
    COC(
        "coc", "Certificate of Conformance",
        "What a new part from the manufacturer usually ships on.",
    ),
    MFR_CERT(
        "mfr_cert", "Manufacturer's certificate",
        "Other manufacturer paperwork — a packing slip with traceability.",
    ),
    AIRLINE_RELEASE(
        "airline_release", "Operator / 145 release",
        "A used serviceable part released by an operator or repair station.",
    ),
    STANDARD_PART(
        "standard_part", "Standard part (AN / MS / NAS)",
        "Hardware to a published spec. A washer has never carried a tag.",
    ),
    CONSUMABLE(
        "consumable", "Consumable (batch + shelf life)",
        "Grease, sealant, lockwire. Record the batch and the shelf life.",
    ),
    OWNER_PRODUCED(
        "owner_produced", "Owner-produced part",
        "Produced by or for the owner. Keep the substantiating data.",
    ),
    NONE_REQUIRED(
        "none_required", "No release document required",
        "Say why. An exception with no reason is refused.",
    );

    /**
     * A certificate must carry its number — the database enforces this too.
     * Without the number the record cannot be found again, which is the only
     * thing it exists for.
     */
    val requiresDocNumber: Boolean
        get() = this == FORM_8130 || this == EASA_FORM_1 || this == DUAL_RELEASE

    /** These are the exceptions, and each must say why. */
    val requiresReason: Boolean
        get() = this == STANDARD_PART || this == CONSUMABLE ||
            this == NONE_REQUIRED || this == OWNER_PRODUCED

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: FORM_8130
    }
}

enum class HFPartCondition(val raw: String, val label: String) {
    NEW("new", "New"),
    NEW_SURPLUS("new_surplus", "New surplus"),
    OVERHAULED("overhauled", "Overhauled"),
    REPAIRED("repaired", "Repaired"),
    INSPECTED("inspected", "Inspected"),
    SERVICEABLE("serviceable", "Serviceable"),
    AS_REMOVED("as_removed", "As removed");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

data class HFPartRelease(
    val id: String,
    val movementId: String? = null,
    val partRequestId: String? = null,
    val workOrderId: String? = null,
    val planeId: String? = null,
    val partNumber: String,
    val description: String? = null,
    val serialNumber: String? = null,
    val lotBatch: String? = null,
    val quantity: Double = 1.0,
    val docType: HFReleaseDocType = HFReleaseDocType.FORM_8130,
    val docNumber: String? = null,
    val issuerName: String? = null,
    val issuerCertNumber: String? = null,
    val issuedOn: String? = null,
    val condition: HFPartCondition? = null,
    val shelfLifeExpiresOn: String? = null,
    val cureDate: String? = null,
    val filePath: String? = null,
    val noDocReason: String? = null,
    val acceptedAt: String? = null,
    val acceptedByName: String? = null,
    val rejectedAt: String? = null,
    val rejectedReason: String? = null,
    val notes: String? = null,
) {
    val isAccepted: Boolean get() = !acceptedAt.isNullOrBlank()
    val isRejected: Boolean get() = !rejectedAt.isNullOrBlank()
    val isPending: Boolean get() = !isAccepted && !isRejected

    /** Out of life is out of life, however good the tag was. */
    val isOutOfLife: Boolean
        get() = runCatching {
            shelfLifeExpiresOn?.let { LocalDate.parse(it) < LocalDate.now() } ?: false
        }.getOrDefault(false)

    val statusLabel: String
        get() = when {
            isRejected -> "Rejected"
            isOutOfLife -> "Out of shelf life"
            isAccepted -> "Accepted"
            else -> "Awaiting inspection"
        }

    /** One line fit to paste into a logbook entry. */
    val traceLine: String
        get() = buildString {
            append(partNumber)
            serialNumber?.takeIf { it.isNotBlank() }?.let { append(" S/N $it") }
            if (docType.requiresDocNumber) {
                docNumber?.takeIf { it.isNotBlank() }?.let { append(" per ${docType.raw} $it") }
            }
            issuerName?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
        }
}

@Serializable
private data class PartReleaseRow(
    val id: String,
    val movement_id: String? = null,
    val part_request_id: String? = null,
    val work_order_id: String? = null,
    val plane_id: String? = null,
    val part_number: String = "",
    val description: String? = null,
    val serial_number: String? = null,
    val lot_batch: String? = null,
    val quantity: Double? = null,
    val doc_type: String? = null,
    val doc_number: String? = null,
    val issuer_name: String? = null,
    val issuer_cert_number: String? = null,
    val issued_on: String? = null,
    val part_condition: String? = null,
    val shelf_life_expires_on: String? = null,
    val cure_date: String? = null,
    val file_path: String? = null,
    val no_doc_reason: String? = null,
    val accepted_at: String? = null,
    val accepted_by_name: String? = null,
    val rejected_at: String? = null,
    val rejected_reason: String? = null,
    val notes: String? = null,
) {
    fun toModel() = HFPartRelease(
        id = id, movementId = movement_id, partRequestId = part_request_id,
        workOrderId = work_order_id, planeId = plane_id, partNumber = part_number,
        description = description, serialNumber = serial_number, lotBatch = lot_batch,
        quantity = quantity ?: 1.0, docType = HFReleaseDocType.from(doc_type),
        docNumber = doc_number, issuerName = issuer_name,
        issuerCertNumber = issuer_cert_number, issuedOn = issued_on,
        condition = HFPartCondition.from(part_condition),
        shelfLifeExpiresOn = shelf_life_expires_on, cureDate = cure_date,
        filePath = file_path, noDocReason = no_doc_reason,
        acceptedAt = accepted_at, acceptedByName = accepted_by_name,
        rejectedAt = rejected_at, rejectedReason = rejected_reason, notes = notes,
    )
}

/** One reason a job is not closeable, from `hf_job_closeout_check`. */
@Serializable
data class HFCloseoutIssue(
    val severity: String = "",
    val code: String = "",
    val detail: String = "",
    val count: Int = 0,
) {
    /**
     * Blockers stop a return to service. Warnings are the shop's call — Hangar
     * Flow does not decide whether they may bill.
     */
    val isBlocker: Boolean get() = severity == "blocker"
}

@Serializable
private data class IdRow(val id: String)

object PartReleaseCloud {

    private val client get() = SupabaseClientProvider.client

    suspend fun fetch(orgId: String): List<HFPartRelease> =
        client.postgrest.from("hf_part_releases").select {
            filter { eq("org_id", orgId) }
        }.decodeList<PartReleaseRow>().map { it.toModel() }

    @Serializable
    private data class ReleaseUpsert(
        val id: String,
        val org_id: String,
        val movement_id: String? = null,
        val part_request_id: String? = null,
        val work_order_id: String? = null,
        val plane_id: String? = null,
        val part_number: String,
        val description: String? = null,
        val serial_number: String? = null,
        val lot_batch: String? = null,
        val quantity: Double,
        val doc_type: String,
        val doc_number: String? = null,
        val issuer_name: String? = null,
        val issuer_cert_number: String? = null,
        val issued_on: String? = null,
        val part_condition: String? = null,
        val shelf_life_expires_on: String? = null,
        val cure_date: String? = null,
        val file_path: String? = null,
        val no_doc_reason: String? = null,
        val notes: String? = null,
        val created_by_user_id: String? = null,
        val created_by_user_name: String? = null,
    )

    private fun blank(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun save(
        orgId: String,
        r: HFPartRelease,
        actorId: String?,
        actorName: String?,
    ): LogbookError {
        if (blank(r.partNumber) == null) return "A release needs a part number."
        if (r.docType.requiresDocNumber && blank(r.docNumber) == null)
            return "A ${r.docType.label} has to carry its number — without it the record " +
                "can't be found again, which is the only thing it exists for."
        if (r.docType.requiresReason && blank(r.noDocReason) == null)
            return "Say why this part needs no release document. An exception with no reason " +
                "is refused."
        return try {
            // Read the row back: RLS refuses by matching nothing, and a release
            // document that "saved" and did not is a part with no paperwork.
            val touched = client.postgrest.from("hf_part_releases").upsert(
                ReleaseUpsert(
                    id = r.id, org_id = orgId, movement_id = r.movementId,
                    part_request_id = r.partRequestId, work_order_id = r.workOrderId,
                    plane_id = r.planeId, part_number = r.partNumber.trim(),
                    description = blank(r.description), serial_number = blank(r.serialNumber),
                    lot_batch = blank(r.lotBatch), quantity = r.quantity,
                    doc_type = r.docType.raw, doc_number = blank(r.docNumber),
                    issuer_name = blank(r.issuerName),
                    issuer_cert_number = blank(r.issuerCertNumber),
                    issued_on = blank(r.issuedOn), part_condition = r.condition?.raw,
                    shelf_life_expires_on = blank(r.shelfLifeExpiresOn),
                    cure_date = blank(r.cureDate), file_path = blank(r.filePath),
                    no_doc_reason = blank(r.noDocReason), notes = blank(r.notes),
                    created_by_user_id = actorId, created_by_user_name = actorName,
                )
            ) { select() }.decodeList<IdRow>()
            if (touched.isEmpty())
                "That release didn't save. You may not have permission to record parts for " +
                    "this shop."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }

    @Serializable
    private data class AcceptPatch(
        val accepted_at: String,
        val accepted_by_user_id: String,
        val accepted_by_name: String,
        val rejected_at: String? = null,
        val rejected_reason: String? = null,
    )

    /**
     * The 43.13 determination. Named `accept` rather than `approve` on purpose:
     * the mechanic is accepting the part as approved for installation, they are
     * not approving anything themselves.
     */
    suspend fun accept(
        r: HFPartRelease,
        userId: String?,
        name: String?,
        nowIso: String,
    ): LogbookError {
        if (userId.isNullOrBlank() || name.isNullOrBlank())
            return "Sign in before accepting a part."
        if (r.isOutOfLife)
            return "That part is past its shelf life (${r.shelfLifeExpiresOn}), so it can't be " +
                "accepted for installation — reject it, or correct the date if it's wrong."
        return try {
            val touched = client.postgrest.from("hf_part_releases").update(
                AcceptPatch(nowIso, userId, name)
            ) { select(); filter { eq("id", r.id) } }.decodeList<IdRow>()
            if (touched.isEmpty())
                "Nothing was accepted — that release is no longer on file. Refresh and check it."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }

    @Serializable
    private data class RejectPatch(
        val rejected_at: String,
        val rejected_reason: String,
        val accepted_at: String? = null,
        val accepted_by_user_id: String? = null,
        val accepted_by_name: String? = null,
    )

    /**
     * Refused at receiving. Kept, never deleted: "we sent one back" is exactly
     * what a shop needs to be able to prove later.
     */
    suspend fun reject(r: HFPartRelease, reason: String, nowIso: String): LogbookError {
        val why = reason.trim()
        if (why.isEmpty()) return "Say why it's being rejected."
        return try {
            val touched = client.postgrest.from("hf_part_releases").update(
                RejectPatch(nowIso, why)
            ) { select(); filter { eq("id", r.id) } }.decodeList<IdRow>()
            if (touched.isEmpty())
                "Nothing was rejected — that release is no longer on file. Refresh and check it."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }

    suspend fun delete(r: HFPartRelease): LogbookError {
        if (r.isAccepted)
            return "An accepted release is evidence. Supersede it with a new one."
        return try {
            val touched = client.postgrest.from("hf_part_releases").delete {
                select(); filter { eq("id", r.id) }
            }.decodeList<IdRow>()
            if (touched.isEmpty())
                "That release wasn't deleted — it may already be accepted, or you can't remove it."
            else null
        } catch (t: Throwable) {
            t.message?.take(240) ?: "Unknown error"
        }
    }

    /** What still stands between this job and a return to service. */
    suspend fun closeout(workOrderId: String): List<HFCloseoutIssue> = try {
        client.postgrest.rpc(
            "hf_job_closeout_check",
            buildJsonObject { put("p_work_order_id", workOrderId) } as JsonObject,
        ).decodeList<HFCloseoutIssue>()
    } catch (t: Throwable) {
        listOf(
            HFCloseoutIssue(
                severity = "warning", code = "check_failed",
                detail = "Couldn't read the close-out checks: ${t.message?.take(120)}",
            )
        )
    }
}
