package com.hangarflow.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The "never guess" rule, which is the safety property behind auto-attach.
 *
 * A wrong guess here puts one customer's squawk on another customer's job and
 * bills it there, silently. Two jobs open on a tail must therefore resolve to
 * NOTHING rather than to the newest — the work stays loose and a human chooses.
 */
class HFOpenJobTest {

    private fun job(id: String, plane: String?, n: Int? = null) =
        HFOpenJob(id = id, planeId = plane, woNumber = n, title = "Job $id")

    @Test fun exactlyOneOpenJobResolves() {
        val jobs = listOf(job("a", "plane-1", 1001))
        assertEquals("a", HFOpenJob.soleOpenJob(jobs, "plane-1")?.id)
    }

    @Test fun twoOpenJobsOnTheSameTailResolveToNothing() {
        val jobs = listOf(job("a", "plane-1", 1001), job("b", "plane-1", 1002))
        assertNull(
            HFOpenJob.soleOpenJob(jobs, "plane-1"),
            "ambiguous: guessing would bill one customer's work to another's job"
        )
    }

    @Test fun threeOrMoreIsAlsoAmbiguous() {
        val jobs = listOf(job("a", "p"), job("b", "p"), job("c", "p"))
        assertNull(HFOpenJob.soleOpenJob(jobs, "p"))
    }

    @Test fun noOpenJobOnThisTailResolvesToNothing() {
        val jobs = listOf(job("a", "plane-2"))
        assertNull(HFOpenJob.soleOpenJob(jobs, "plane-1"))
        assertNull(HFOpenJob.soleOpenJob(emptyList(), "plane-1"))
    }

    /** Other aeroplanes' jobs must never be counted — this is the case that
     *  would turn "one job each on two tails" into a false ambiguity, or worse,
     *  attach across tails. */
    @Test fun jobsOnOtherTailsAreIgnored() {
        val jobs = listOf(job("a", "plane-1"), job("b", "plane-2"), job("c", "plane-3"))
        assertEquals("a", HFOpenJob.soleOpenJob(jobs, "plane-1")?.id)
        assertEquals("b", HFOpenJob.soleOpenJob(jobs, "plane-2")?.id)
    }

    @Test fun aMissingOrBlankTailResolvesToNothing() {
        val jobs = listOf(job("a", "plane-1"))
        assertNull(HFOpenJob.soleOpenJob(jobs, null))
        assertNull(HFOpenJob.soleOpenJob(jobs, ""))
        assertNull(HFOpenJob.soleOpenJob(jobs, "   "))
    }

    /** A job row with no plane must not match a null-ish query and sweep up
     *  everything. */
    @Test fun aJobWithNoPlaneNeverMatches() {
        val jobs = listOf(job("orphan", null))
        assertNull(HFOpenJob.soleOpenJob(jobs, null))
        assertNull(HFOpenJob.soleOpenJob(jobs, "plane-1"))
    }

    @Test fun theLabelIsWhatPeopleSayOutLoud() {
        assertEquals("WO 1042", job("a", "p", 1042).label)
        assertEquals("WO —", job("a", "p", null).label)
    }
}
