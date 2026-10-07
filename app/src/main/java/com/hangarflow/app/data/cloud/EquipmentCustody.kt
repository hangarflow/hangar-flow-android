package com.hangarflow.app.data.cloud

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * Who has this tool right now, and which hangar it is in.
 *
 * The problem is not sync. It is the STALE CHECKOUT: people take a tool and
 * never check it back in, the screen says Mike has the torque wrench three
 * weeks after Mike put it back, and once the screen has been wrong twice
 * nobody looks at it again. Everything here is shaped to make the record cheap
 * to keep true rather than to make checkout richer.
 *
 * Three decisions carry that:
 *
 *   * **One action, not two.** There is no "check out" button and "check in"
 *     button. There is a scan (or a tap), and it does whatever is correct next.
 *     A mechanic holding a wrench should never have to decide which verb they
 *     are performing.
 *   * **Custody is a log.** `hf_equipment_custody` is append-only; the current
 *     holder is the latest row. History comes free, and two people grabbing the
 *     same tool in two hangars becomes two rows to read rather than one field
 *     where the second write silently erases the first.
 *   * **Whoever is holding it wins.** Scanning a tool somebody else has does
 *     not refuse — it records a hand-off. The app follows physical reality
 *     instead of trying to referee it, which is the only way the log stays
 *     true.
 */

// ── What a scan does next ─────────────────────────────────────────────

@Serializable
enum class HFCustodyAction(val raw: String) {
    /** Somebody now has it. */
    OUT("out"),

    /** It is back in the crib / on its hook. */
    IN("in");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: IN
    }
}

/** A hangar, bay, bench, crib or truck — a place a tool can be. */
@Serializable
data class HFShopLocation(
    val id: String,
    val name: String,
    val kind: String = "hangar",
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
) {
    val kindLabel: String
        get() = when (kind) {
            "bay" -> "Bay"
            "bench" -> "Bench"
            "crib" -> "Tool crib"
            "vehicle" -> "Vehicle"
            "hangar" -> "Hangar"
            else -> "Location"
        }
}

/**
 * The current custody of one piece of equipment — one row per tool, read from
 * the `hf_equipment_current_custody` view.
 *
 * Absent from the map entirely means the tool has no custody history at all,
 * which reads the same as "in the crib" and is handled by [HFCustodyRules].
 */
@Serializable
data class HFEquipmentCustody(
    val equipmentId: String,
    val action: HFCustodyAction,
    val actorUserId: String,
    val actorName: String,
    val locationId: String? = null,
    val locationName: String = "",
    val at: String,
) {
    val isOut: Boolean get() = action == HFCustodyAction.OUT

    /** When this record was made, or null if the timestamp is unparseable. */
    val atInstant: Instant?
        get() = runCatching { Instant.parse(at) }.getOrNull()
}

// ── The rules, in one place, so four clients cannot disagree ──────────

/**
 * Every decision about custody lives here and nowhere else.
 *
 * It is pure — no network, no clock of its own — so the behaviour four
 * platforms show is one tested object rather than four similar-looking
 * `if` blocks that drift apart. `now` is a parameter for the same reason:
 * a rule that reads the clock itself cannot be tested.
 */
object HFCustodyRules {

    /**
     * How long a tool can be out before the record stops being believable.
     *
     * Twelve hours, which is "longer than any shift." The sharper test is
     * whether the holder is still clocked in — the system knows when Mike went
     * home — but who is on the clock is admin-only information, so deciding
     * staleness from it would either leak that to every tech or read as
     * "everything is stale" for one. Elapsed time is blunt, honest, and leaks
     * nothing.
     */
    val staleAfter: Duration = Duration.ofHours(12)

    /**
     * What a scan should do, given who is scanning.
     *
     * The only case worth arguing about is scanning a tool someone else has.
     * This returns OUT — a hand-off, not a refusal — because the person
     * scanning it is physically holding it, and software that argues with that
     * trains people to stop scanning.
     */
    fun nextAction(current: HFEquipmentCustody?, meUserId: String): HFCustodyAction =
        when {
            current == null -> HFCustodyAction.OUT
            !current.isOut -> HFCustodyAction.OUT
            current.actorUserId == meUserId -> HFCustodyAction.IN
            else -> HFCustodyAction.OUT
        }

    /** True when scanning would take the tool off somebody else, not return it. */
    fun isHandoff(current: HFEquipmentCustody?, meUserId: String): Boolean =
        current != null && current.isOut && current.actorUserId != meUserId

    /**
     * Out for longer than anyone works in a day, so nobody has said otherwise.
     *
     * Only an OUT record can go stale: a tool sitting in the crib for a month
     * is not a problem, it is a tool.
     */
    fun isStale(current: HFEquipmentCustody?, now: Instant): Boolean {
        if (current == null || !current.isOut) return false
        val at = current.atInstant ?: return false
        return at.isBefore(now.minus(staleAfter))
    }

    /**
     * What the equipment list should say under the tool's name.
     *
     * Deliberately names the person. "Checked out" tells a mechanic nothing
     * they can act on; "Keoni has it" tells them who to go ask.
     */
    fun statusLine(current: HFEquipmentCustody?, now: Instant): String {
        if (current == null || !current.isOut) {
            val where = current?.locationName?.takeIf { it.isNotBlank() }
            return if (where != null) "In $where" else "Available"
        }
        val who = current.actorName.takeIf { it.isNotBlank() } ?: "Someone"
        val where = current.locationName.takeIf { it.isNotBlank() }
        val base = if (where != null) "$who has it — $where" else "$who has it"
        return if (isStale(current, now)) "$base (check?)" else base
    }

    /**
     * Resolve current custody from a raw log.
     *
     * The view does this server-side and is what the clients read; this exists
     * so the tie-break is written down and tested. Two scans in the same
     * millisecond must resolve to the same winner on every device, or two
     * mechanics see two different answers — so `id` breaks the tie, exactly as
     * the view's `order by at desc, id desc` does.
     */
    fun currentFromLog(rows: List<Triple<String, HFEquipmentCustody, String>>): HFEquipmentCustody? =
        rows.maxWithOrNull(
            compareBy({ it.second.atInstant ?: Instant.EPOCH }, { it.third })
        )?.second
}

// ── Wire format ───────────────────────────────────────────────────────

@Serializable
private data class ShopLocationRow(
    val id: String,
    val org_id: String? = null,
    val name: String = "",
    val kind: String = "hangar",
    val is_active: Boolean = true,
    val sort_order: Int = 0,
) {
    fun toModel() = HFShopLocation(
        id = id, name = name, kind = kind,
        isActive = is_active, sortOrder = sort_order,
    )
}

@Serializable
private data class CurrentCustodyRow(
    val equipment_id: String,
    val action: String? = null,
    val actor_user_id: String? = null,
    val actor_name: String? = null,
    val location_id: String? = null,
    val location_name: String? = null,
    val at: String? = null,
) {
    fun toModel(): HFEquipmentCustody? {
        val actor = actor_user_id ?: return null
        val stamp = at ?: return null
        return HFEquipmentCustody(
            equipmentId = equipment_id,
            action = HFCustodyAction.from(action),
            actorUserId = actor,
            actorName = actor_name ?: "",
            locationId = location_id,
            locationName = location_name ?: "",
            at = stamp,
        )
    }
}

@Serializable
private data class CustodyInsertRow(
    val org_id: String,
    val equipment_id: String,
    val action: String,
    val actor_user_id: String,
    val actor_name: String,
    val location_id: String? = null,
    val location_name: String = "",
    val source_device: String? = null,
    val notes: String? = null,
)

@Serializable
private data class CustodyIdOnly(val id: String)

// ── Cloud access ──────────────────────────────────────────────────────

object EquipmentCustodyCloud {

    private val client get() = SupabaseClientProvider.client

    /** The shop's hangars and benches, in the order an admin arranged them. */
    suspend fun fetchLocations(orgId: String): List<HFShopLocation> =
        client.postgrest.from("hf_shop_locations")
            .select { filter { eq("org_id", orgId) } }
            .decodeList<ShopLocationRow>()
            .map { it.toModel() }
            .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))

    /**
     * Current custody for every tool in the shop, keyed by equipment id.
     *
     * One row per tool, not per scan — the view collapses the log. A tool with
     * no history is simply absent from the map.
     */
    suspend fun fetchCurrentCustody(orgId: String): Map<String, HFEquipmentCustody> =
        client.postgrest.from("hf_equipment_current_custody")
            .select { filter { eq("org_id", orgId) } }
            .decodeList<CurrentCustodyRow>()
            .mapNotNull { it.toModel() }
            .associateBy { it.equipmentId }

    /** The last few movements of one tool, newest first — the paper trail. */
    suspend fun fetchHistory(equipmentId: String, limit: Long = 25): List<HFEquipmentCustody> =
        client.postgrest.from("hf_equipment_custody")
            .select {
                filter { eq("equipment_id", equipmentId) }
                order("at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit)
            }
            .decodeList<CurrentCustodyRow>()
            .mapNotNull { it.toModel() }

    /**
     * Record a take or a return. Returns null on success, else why not.
     *
     * The insert reads its row back. RLS refuses by matching ZERO ROWS rather
     * than raising, so an insert the policy filtered out looks identical to one
     * that worked — and a checkout that silently did not happen is worse than
     * no checkout at all, because the next person trusts it.
     */
    suspend fun record(
        orgId: String,
        equipmentId: String,
        action: HFCustodyAction,
        actorUserId: String,
        actorName: String,
        locationId: String? = null,
        locationName: String = "",
        sourceDevice: String? = null,
        notes: String? = null,
    ): String? = try {
        val touched = client.postgrest.from("hf_equipment_custody")
            .insert(
                CustodyInsertRow(
                    org_id = orgId,
                    equipment_id = equipmentId,
                    action = action.raw,
                    actor_user_id = actorUserId,
                    actor_name = actorName,
                    location_id = locationId,
                    location_name = locationName,
                    source_device = sourceDevice,
                    notes = notes?.takeIf { it.isNotBlank() },
                )
            ) { select() }
            .decodeList<CustodyIdOnly>()

        if (touched.isEmpty())
            "That didn't save. You may not have permission to move this shop's tools."
        else { hfAnnounce("equipment_custody_changed"); null }
    } catch (t: Throwable) {
        // The insert policy requires actor_user_id = auth.uid(), so the common
        // real failure is a stale session rather than a malformed row.
        hfCustodyMessage(t)
    }

    /** Admin-only: add a hangar or bench to the shop's list. */
    suspend fun createLocation(
        orgId: String,
        name: String,
        kind: String = "hangar",
    ): String? = try {
        val clean = name.trim()
        if (clean.isEmpty()) "Give the location a name."
        else {
            val touched = client.postgrest.from("hf_shop_locations")
                .insert(
                    mapOf(
                        "org_id" to orgId,
                        "name" to clean,
                        "kind" to kind,
                    )
                ) { select() }
                .decodeList<CustodyIdOnly>()
            if (touched.isEmpty())
                "That location wasn't added — only an admin can change the shop's locations."
            else { hfAnnounce("equipment_custody_changed"); null }
        }
    } catch (t: Throwable) {
        // The unique (org_id, name) constraint is the whole reason this table
        // exists, so saying so beats a Postgres error code.
        if ((t.message ?: "").contains("unique", ignoreCase = true))
            "There's already a location called that."
        else hfCustodyMessage(t)
    }
}

private fun hfCustodyMessage(t: Throwable): String =
    t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
