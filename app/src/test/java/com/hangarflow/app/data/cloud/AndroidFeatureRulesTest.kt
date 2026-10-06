package com.hangarflow.app.data.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate

/**
 * The derived rules of the three Android ports — part releases, the guided
 * walkthrough, the fault tree.
 *
 * These are the bits a port gets subtly wrong: an off-by-one in "which step am
 * I on", a status that reports Accepted for a part that is out of shelf life, a
 * summary that silently drops what was ruled out. None of them throws; all of
 * them are wrong on a screen a mechanic trusts.
 */
class AndroidFeatureRulesTest {

    // ── Part releases ─────────────────────────────────────────────────

    @Test
    fun onlyRealCertificatesOweANumber() {
        // 8130-3, EASA Form 1 and a dual release are findable documents; the
        // number is the only way to find one again.
        listOf(
            HFReleaseDocType.FORM_8130,
            HFReleaseDocType.EASA_FORM_1,
            HFReleaseDocType.DUAL_RELEASE,
        ).forEach { assertTrue(it.requiresDocNumber, "$it should owe a number") }

        HFReleaseDocType.entries
            .filterNot { it.requiresDocNumber }
            .forEach { assertFalse(it.requiresReason && it.requiresDocNumber) }
    }

    @Test
    fun theExceptionsAreTheOnesThatOweAReason() {
        // A washer has never carried a tag. Treating that as a failure is how
        // shops learn to fake paperwork, so the exception is first class — and
        // it has to say why.
        listOf(
            HFReleaseDocType.STANDARD_PART,
            HFReleaseDocType.CONSUMABLE,
            HFReleaseDocType.NONE_REQUIRED,
            HFReleaseDocType.OWNER_PRODUCED,
        ).forEach { assertTrue(it.requiresReason, "$it should owe a reason") }

        assertFalse(HFReleaseDocType.FORM_8130.requiresReason)
        assertFalse(HFReleaseDocType.COC.requiresReason)
    }

    private fun release(
        shelfLife: String? = null,
        accepted: String? = null,
        rejected: String? = null,
        sn: String? = null,
        docType: HFReleaseDocType = HFReleaseDocType.FORM_8130,
        docNumber: String? = null,
        issuer: String? = null,
    ) = HFPartRelease(
        id = "r", partNumber = "CH48110-1", serialNumber = sn,
        docType = docType, docNumber = docNumber, issuerName = issuer,
        shelfLifeExpiresOn = shelfLife, acceptedAt = accepted, rejectedAt = rejected,
    )

    @Test
    fun outOfShelfLifeOutRanksAccepted() {
        // Out of life is out of life, however good the tag was. If "Accepted"
        // won this comparison the shelf-life date would be decoration.
        val expired = release(
            shelfLife = LocalDate.now().minusDays(1).toString(),
            accepted = "2026-01-01T00:00:00Z",
        )
        assertTrue(expired.isOutOfLife)
        assertEquals("Out of shelf life", expired.statusLabel)
    }

    @Test
    fun rejectedOutRanksEverything() {
        val r = release(
            shelfLife = LocalDate.now().minusDays(1).toString(),
            rejected = "2026-01-01T00:00:00Z",
        )
        assertEquals("Rejected", r.statusLabel)
    }

    @Test
    fun aPartInDateReadsAsAcceptedOrPending() {
        val good = LocalDate.now().plusDays(30).toString()
        assertEquals("Awaiting inspection", release(shelfLife = good).statusLabel)
        assertEquals(
            "Accepted",
            release(shelfLife = good, accepted = "2026-01-01T00:00:00Z").statusLabel,
        )
        assertFalse(release(shelfLife = good).isOutOfLife)
        assertFalse(release().isOutOfLife, "no shelf life is not an expired shelf life")
    }

    @Test
    fun aMalformedShelfLifeDateDoesNotBlockThePart() {
        // A bad date should not quietly condemn a good part, nor throw.
        assertFalse(release(shelfLife = "not-a-date").isOutOfLife)
    }

    @Test
    fun theTraceLineIsFitToPasteIntoALogbook() {
        val r = release(
            sn = "A1234", docType = HFReleaseDocType.FORM_8130,
            docNumber = "ARC-88412", issuer = "Acme Repair Station",
        )
        assertEquals("CH48110-1 S/N A1234 per 8130-3 ARC-88412 (Acme Repair Station)", r.traceLine)
    }

    @Test
    fun aStandardPartTraceLineClaimsNoCertificate() {
        // A washer must not read "per 8130-3" just because the field is empty.
        val r = release(docType = HFReleaseDocType.STANDARD_PART, docNumber = "ignored")
        assertEquals("CH48110-1", r.traceLine)
    }

    // ── The guided walkthrough ────────────────────────────────────────

    private fun run(done: Set<Int>, steps: Int = 4, completed: String? = null) = HFGuidedRun(
        id = "g", taskTitle = "Replace the cabin door seal",
        steps = (1..steps).map { HFProcedureStep(n = it, what = "step $it") },
        doneSteps = done, completedAt = completed,
    )

    @Test
    fun theDeckOpensOnTheFirstStepNotYetTicked() {
        assertEquals(0, run(emptySet()).currentIndex)
        assertEquals(1, run(setOf(1)).currentIndex)
        // And skips a gap rather than stopping at it: ticking 1 and 3 leaves 2.
        assertEquals(1, run(setOf(1, 3)).currentIndex)
    }

    @Test
    fun aFinishedDeckHasNoCurrentStep() {
        assertNull(run(setOf(1, 2, 3, 4)).currentIndex)
        assertEquals(0, run(setOf(1, 2, 3, 4)).remaining)
    }

    @Test
    fun progressCountsStepsNotGuesses() {
        assertEquals(0.0, run(emptySet()).progress)
        assertEquals(0.5, run(setOf(1, 2)).progress)
        assertEquals(1.0, run(setOf(1, 2, 3, 4)).progress)
        assertEquals(2, run(setOf(1, 2)).remaining)
    }

    @Test
    fun anEmptyDeckDoesNotDivideByZero() {
        val empty = HFGuidedRun(id = "g", steps = emptyList())
        assertEquals(0.0, empty.progress)
        assertEquals(0, empty.remaining)
        assertNull(empty.currentIndex)
    }

    @Test
    fun completionIsTheStoredStampNotACount() {
        // Ticking every step and the run being CLOSED are different facts; the
        // database owns the second one.
        assertFalse(run(setOf(1, 2, 3, 4)).isComplete)
        assertTrue(run(setOf(1, 2, 3, 4), completed = "2026-10-06T10:00:00Z").isComplete)
    }

    // ── The fault tree ────────────────────────────────────────────────

    private fun cause(n: Int, text: String, outcome: HFCauseOutcome) =
        HFTroubleCause(n = n, cause = text, outcome = outcome.raw)

    private fun tree(vararg c: HFTroubleCause) =
        HFTroubleshootRun(id = "t", symptom = "Door seal leaks", causes = c.toList())

    @Test
    fun nothingCheckedYieldsNoSummary() {
        assertNull(tree(cause(1, "Seal worn", HFCauseOutcome.UNTESTED)).eliminationSummary)
    }

    @Test
    fun whatWasRuledOutIsWrittenDown() {
        // The thing that never gets written down, and the thing the next
        // mechanic most wants.
        val s = tree(
            cause(1, "Seal worn", HFCauseOutcome.RULED_OUT),
            cause(2, "Latch out of rig", HFCauseOutcome.RULED_OUT),
            cause(3, "Inflation line kinked", HFCauseOutcome.UNTESTED),
        ).eliminationSummary!!
        assertEquals("Ruled out: Seal worn; Latch out of rig.", s)
    }

    @Test
    fun findingItRecordsBothWhatWasEliminatedAndWhatItWas() {
        val t = tree(
            cause(1, "Seal worn", HFCauseOutcome.RULED_OUT),
            cause(2, "Inflation line kinked", HFCauseOutcome.CONFIRMED),
        )
        assertEquals("Inflation line kinked", t.confirmed?.cause)
        assertEquals(1, t.ruledOut)
        assertEquals(
            "Ruled out: Seal worn. Found: Inflation line kinked.",
            t.eliminationSummary,
        )
    }

    @Test
    fun anUnknownOutcomeIsTreatedAsUntestedRatherThanLost() {
        // A newer client may write an outcome this build has never heard of.
        // Counting it as ruled out would be a lie about the aeroplane.
        val t = tree(HFTroubleCause(n = 1, cause = "Something", outcome = "invented"))
        assertEquals(HFCauseOutcome.UNTESTED, t.causes.first().state)
        assertEquals(0, t.ruledOut)
        assertNull(t.eliminationSummary)
    }
}
