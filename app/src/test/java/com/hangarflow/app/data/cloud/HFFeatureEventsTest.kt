package com.hangarflow.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The event router decides, per incoming realtime event, whether this device
 * owes a full eighteen-table snapshot pull or just one counter bump. Getting
 * that backwards is expensive in one direction and silently stale in the
 * other, so both directions are pinned here.
 */
class HFFeatureEventsTest {

    /** A known type is handled here and must NOT fall through to a snapshot. */
    @Test
    fun `known feature events are consumed`() {
        for (t in listOf(
            "logbook_changed", "part_release_changed",
            "guided_run_changed", "troubleshoot_changed",
        )) {
            assertTrue("$t should be consumed, not snapshot-pulled",
                HFFeatureEvents.consume(t))
        }
    }

    /**
     * Everything else still owes the snapshot. `snapshot_push` is the existing
     * default event type and is the case that must keep working; the made-up
     * one stands in for an event type a later release adds, which should be
     * stale-but-correct rather than dropped.
     */
    @Test
    fun `unknown event types still demand a full pull`() {
        assertFalse(HFFeatureEvents.consume("snapshot_push"))
        assertFalse(HFFeatureEvents.consume("something_a_later_release_adds"))
        assertFalse(HFFeatureEvents.consume(""))
    }

    /** One event moves one counter, and only that one. */
    @Test
    fun `each event moves only its own counter`() {
        val logbook = HFFeatureEvents.logbook.value
        val release = HFFeatureEvents.partRelease.value
        val guided = HFFeatureEvents.guidedRun.value
        val tree = HFFeatureEvents.troubleshoot.value

        HFFeatureEvents.consume("logbook_changed")

        assertEquals(logbook + 1, HFFeatureEvents.logbook.value)
        assertEquals(release, HFFeatureEvents.partRelease.value)
        assertEquals(guided, HFFeatureEvents.guidedRun.value)
        assertEquals(tree, HFFeatureEvents.troubleshoot.value)
    }

    /**
     * Twenty walkthrough steps on somebody else's tablet is the case this
     * whole mechanism exists for: twenty bumps, zero snapshot pulls.
     */
    @Test
    fun `a burst of steps never asks for a snapshot`() {
        val before = HFFeatureEvents.guidedRun.value
        repeat(20) { assertTrue(HFFeatureEvents.consume("guided_run_changed")) }
        assertEquals(before + 20, HFFeatureEvents.guidedRun.value)
    }
}
