package com.hangarflow.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The custody rules decide what a scan does. Getting them wrong is not a
 * cosmetic bug: it either refuses a mechanic who is physically holding the
 * tool, or it reports a tool as available while it is in somebody's truck.
 *
 * The hand-off case is the one worth pinning hardest — it is the case a
 * "check out / check in" toggle gets wrong, and the reason there is one button
 * instead of two.
 */
class EquipmentCustodyRulesTest {

    private val me = "11111111-1111-4111-8111-111111111111"
    private val keoni = "22222222-2222-4222-8222-222222222222"
    private val now: Instant = Instant.parse("2026-10-06T18:00:00Z")

    private fun custody(
        action: HFCustodyAction,
        actor: String,
        at: String = "2026-10-06T17:00:00Z",
        name: String = "Keoni",
        where: String = "",
    ) = HFEquipmentCustody(
        equipmentId = "eq-1", action = action, actorUserId = actor,
        actorName = name, locationName = where, at = at,
    )

    // ── What a scan does ──────────────────────────────────────────────

    @Test
    fun `a tool with no history goes out to whoever scans it`() {
        assertEquals(HFCustodyAction.OUT, HFCustodyRules.nextAction(null, me))
    }

    @Test
    fun `a tool in the crib goes out`() {
        val back = custody(HFCustodyAction.IN, keoni)
        assertEquals(HFCustodyAction.OUT, HFCustodyRules.nextAction(back, me))
    }

    @Test
    fun `scanning my own checkout returns it`() {
        val mine = custody(HFCustodyAction.OUT, me)
        assertEquals(HFCustodyAction.IN, HFCustodyRules.nextAction(mine, me))
    }

    /**
     * The case the whole design turns on. Scanning a tool Keoni has must NOT
     * check it in on his behalf — that would report the tool in the crib while
     * it is in my hand. It is a hand-off: out, to me.
     */
    @Test
    fun `scanning someone elses tool is a handoff not a return`() {
        val theirs = custody(HFCustodyAction.OUT, keoni)
        assertEquals(HFCustodyAction.OUT, HFCustodyRules.nextAction(theirs, me))
        assertTrue(HFCustodyRules.isHandoff(theirs, me))
    }

    @Test
    fun `my own checkout is not a handoff`() {
        assertFalse(HFCustodyRules.isHandoff(custody(HFCustodyAction.OUT, me), me))
    }

    @Test
    fun `a tool in the crib is never a handoff`() {
        assertFalse(HFCustodyRules.isHandoff(custody(HFCustodyAction.IN, keoni), me))
        assertFalse(HFCustodyRules.isHandoff(null, me))
    }

    // ── Staleness ─────────────────────────────────────────────────────

    @Test
    fun `an hour old checkout is believable`() {
        assertFalse(HFCustodyRules.isStale(custody(HFCustodyAction.OUT, keoni), now))
    }

    @Test
    fun `a two day old checkout is not`() {
        val old = custody(HFCustodyAction.OUT, keoni, at = "2026-10-04T17:00:00Z")
        assertTrue(HFCustodyRules.isStale(old, now))
    }

    /** A tool resting in the crib for a month is a tool, not a problem. */
    @Test
    fun `a returned tool never goes stale`() {
        val old = custody(HFCustodyAction.IN, keoni, at = "2026-01-01T00:00:00Z")
        assertFalse(HFCustodyRules.isStale(old, now))
        assertFalse(HFCustodyRules.isStale(null, now))
    }

    /** Exactly at the boundary is not yet stale — strictly older is. */
    @Test
    fun `the twelve hour boundary is exclusive`() {
        val exactly = custody(HFCustodyAction.OUT, keoni, at = "2026-10-06T06:00:00Z")
        assertFalse(HFCustodyRules.isStale(exactly, now))
        val aSecondMore = custody(HFCustodyAction.OUT, keoni, at = "2026-10-06T05:59:59Z")
        assertTrue(HFCustodyRules.isStale(aSecondMore, now))
    }

    /** An unparseable timestamp must not read as "infinitely old". */
    @Test
    fun `a garbage timestamp is not stale`() {
        val bad = custody(HFCustodyAction.OUT, keoni, at = "not a date")
        assertFalse(HFCustodyRules.isStale(bad, now))
    }

    // ── What the list says ────────────────────────────────────────────

    /** Naming the person is the point — "checked out" is not actionable. */
    @Test
    fun `the status line names who has it`() {
        val theirs = custody(HFCustodyAction.OUT, keoni, name = "Keoni", where = "Hangar B3")
        assertEquals("Keoni has it — Hangar B3", HFCustodyRules.statusLine(theirs, now))
    }

    @Test
    fun `a stale checkout asks the question`() {
        val old = custody(HFCustodyAction.OUT, keoni, at = "2026-10-01T00:00:00Z", name = "Keoni")
        assertTrue(HFCustodyRules.statusLine(old, now).endsWith("(check?)"))
    }

    @Test
    fun `an unknown holder still reads sensibly`() {
        val anon = custody(HFCustodyAction.OUT, keoni, name = "")
        assertEquals("Someone has it", HFCustodyRules.statusLine(anon, now))
    }

    @Test
    fun `a tool in the crib reads as where it is`() {
        val back = custody(HFCustodyAction.IN, keoni, where = "Tool crib")
        assertEquals("In Tool crib", HFCustodyRules.statusLine(back, now))
        assertEquals("Available", HFCustodyRules.statusLine(null, now))
    }

    // ── The tie-break ─────────────────────────────────────────────────

    /**
     * Two scans in the same millisecond must resolve to the same winner on
     * every device. If they don't, two mechanics in two hangars see two
     * different answers and both are "right". `id` breaks it, matching the
     * view's `order by at desc, id desc`.
     */
    @Test
    fun `simultaneous scans resolve the same way everywhere`() {
        val sameMoment = "2026-10-06T17:00:00Z"
        val rows = listOf(
            Triple("a", custody(HFCustodyAction.OUT, me, at = sameMoment, name = "Me"), "aaa"),
            Triple("b", custody(HFCustodyAction.OUT, keoni, at = sameMoment, name = "Keoni"), "zzz"),
        )
        assertEquals("Keoni", HFCustodyRules.currentFromLog(rows)?.actorName)
        // Same rows, opposite order in — same winner out.
        assertEquals("Keoni", HFCustodyRules.currentFromLog(rows.reversed())?.actorName)
    }

    @Test
    fun `a later scan wins regardless of id`() {
        val rows = listOf(
            Triple("a", custody(HFCustodyAction.OUT, me, at = "2026-10-06T17:30:00Z", name = "Me"), "zzz"),
            Triple("b", custody(HFCustodyAction.IN, keoni, at = "2026-10-06T17:00:00Z", name = "Keoni"), "aaa"),
        )
        assertEquals("Me", HFCustodyRules.currentFromLog(rows)?.actorName)
    }

    @Test
    fun `an empty log has no holder`() {
        assertNull(HFCustodyRules.currentFromLog(emptyList()))
    }
}
