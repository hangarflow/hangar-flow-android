package com.hangarflow.app.data.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The regulatory rules, asserted on the ANDROID port.
 *
 * These are the same rules the Apple client enforces. They are written out
 * again here rather than trusted to a careful port, because the failure mode is
 * silent: a Windows client that lets an A&P sign an annual produces a logbook
 * entry that looks exactly like a valid one and is not. A rule that holds on a
 * Mac and not on the shop's Windows machine is not a rule.
 *
 * The raw strings matter as much as the logic — they are what goes in the
 * database column, so a typo here is a row the Apple clients cannot read back.
 */
class LogbookRulesTest {

    // ── The wire format must match Apple exactly ──────────────────────

    @Test
    fun entryTypeRawValuesAreTheOnesTheDatabaseStores() {
        // Checked against HFLogEntryType in HFLogbookFeature.swift. The two
        // that do not follow the enum name are the ones worth pinning.
        assertEquals("100_hour", HFLogEntryType.ONE_HUNDRED_HOUR.raw)
        assertEquals("ad_compliance", HFLogEntryType.AD_COMPLIANCE.raw)
        assertEquals("major_repair", HFLogEntryType.MAJOR_REPAIR.raw)
        assertEquals("major_alteration", HFLogEntryType.MAJOR_ALTERATION.raw)
        assertEquals("altimeter_static", HFLogEntryType.ALTIMETER_STATIC.raw)
        assertEquals("custom", HFLogEntryType.CUSTOM.raw)
    }

    @Test
    fun logSectionRawValuesMatch() {
        assertEquals(
            listOf("airframe", "engine1", "engine2", "engine3", "prop1", "prop2", "apu"),
            HFLogSection.entries.map { it.raw },
        )
    }

    @Test
    fun anUnknownRawValueFallsBackRatherThanThrowing() {
        // A newer client may write a type this build has never heard of. Losing
        // the row entirely would be worse than showing it as maintenance.
        assertEquals(HFLogEntryType.MAINTENANCE, HFLogEntryType.from("something_new"))
        assertEquals(HFLogSection.AIRFRAME, HFLogSection.from(null))
    }

    // ── Who may sign what ─────────────────────────────────────────────

    @Test
    fun anAnnualAndAProgressiveNeedAnIA() {
        // 14 CFR 43.7 / 91.409: an A&P alone cannot approve an annual for
        // return to service.
        assertTrue(HFLogEntryType.ANNUAL.needsIA)
        assertTrue(HFLogEntryType.PROGRESSIVE.needsIA)
    }

    @Test
    fun aHundredHourDoesNotNeedAnIA() {
        // The common confusion, and an expensive one in the other direction:
        // a 100-hour IS an inspection and DOES need an airworthiness finding,
        // but an A&P may sign it.
        assertFalse(HFLogEntryType.ONE_HUNDRED_HOUR.needsIA)
        assertTrue(HFLogEntryType.ONE_HUNDRED_HOUR.isInspection)
        assertTrue(HFLogEntryType.ONE_HUNDRED_HOUR.needsAirworthinessFinding)
    }

    @Test
    fun ordinaryMaintenanceIsNotAnInspection() {
        listOf(
            HFLogEntryType.MAINTENANCE, HFLogEntryType.PREVENTIVE,
            HFLogEntryType.AD_COMPLIANCE, HFLogEntryType.TRANSPONDER,
            HFLogEntryType.ELT, HFLogEntryType.ALTIMETER_STATIC,
        ).forEach {
            assertFalse(it.isInspection, "$it should not demand an airworthiness finding")
            assertFalse(it.needsIA, "$it should not demand an IA")
        }
    }

    @Test
    fun onlyAMajorRepairOrAlterationOwesForm337() {
        assertTrue(HFLogEntryType.MAJOR_REPAIR.needs337)
        assertTrue(HFLogEntryType.MAJOR_ALTERATION.needs337)
        HFLogEntryType.entries
            .filterNot { it == HFLogEntryType.MAJOR_REPAIR || it == HFLogEntryType.MAJOR_ALTERATION }
            .forEach { assertFalse(it.needs337, "$it should not demand a 337") }
    }

    // ── How the entry reads ───────────────────────────────────────────

    private fun entry(
        discrepancy: String? = null,
        body: String = "Replaced the main cabin door seal.",
        signedAt: String? = null,
        certType: String? = null,
        certNumber: String? = null,
        name: String? = null,
        station: String? = null,
        voidReason: String? = null,
        type: HFLogEntryType = HFLogEntryType.MAINTENANCE,
        custom: String? = null,
    ) = HFLogbookEntry(
        id = "e", planeId = "p", entryDate = "2026-10-06", body = body,
        reportedDiscrepancy = discrepancy, signedAt = signedAt,
        signerCertType = certType, signerCertNumber = certNumber, signedByName = name,
        repairStationNumber = station, voidReason = voidReason,
        entryType = type, customTypeLabel = custom,
    )

    @Test
    fun aDiscrepancyAndItsCorrectiveActionReadInThatOrder() {
        val r = entry(discrepancy = "Air leak at the main cabin door.").renderedBody
        assertTrue(r.startsWith("Discrepancy: Air leak at the main cabin door."), r)
        assertTrue(r.contains("Corrective action: Replaced the main cabin door seal."), r)
    }

    @Test
    fun scheduledWorkWithNoComplaintIsJustTheEntry() {
        // An oil change is not a complaint. Printing "Discrepancy: " above it
        // is how entries end up reading "Discrepancy: N/A" forever.
        assertEquals("Replaced the main cabin door seal.", entry().renderedBody)
        assertEquals("Replaced the main cabin door seal.", entry(discrepancy = "   ").renderedBody)
    }

    @Test
    fun aDraftHasNoSignatureBlock() {
        assertNull(entry().signatureBlock)
        assertEquals("Draft", entry().statusLabel)
        assertFalse(entry().isSigned)
    }

    @Test
    fun aSignedEntryNamesTheCertificate() {
        val e = entry(
            signedAt = "2026-10-06T18:00:00Z", name = "Keoni R.",
            certType = "A&P/IA", certNumber = "3412887",
        )
        assertTrue(e.isSigned)
        assertEquals("Signed", e.statusLabel)
        val block = e.signatureBlock!!
        assertTrue(block.contains("Keoni R."), block)
        assertTrue(block.contains("A&P/IA"), block)
        assertTrue(block.contains("3412887"), block)
    }

    @Test
    fun aRepairStationNumberAppearsWhenThereIsOne() {
        val e = entry(
            signedAt = "2026-10-06T18:00:00Z", name = "Shop",
            certType = "Repair Station", certNumber = "X1Y", station = "ABC123",
        )
        assertTrue(e.signatureBlock!!.contains("Repair Station ABC123"))
        assertFalse(
            entry(signedAt = "x", name = "A", certType = "A&P", certNumber = "1")
                .signatureBlock!!.contains("Repair Station"),
        )
    }

    @Test
    fun aVoidedEntryStillReadsAsAnEntry() {
        // Voiding never removes it. The original text stays in the record,
        // which is the entire point of a maintenance record.
        val e = entry(
            signedAt = "2026-10-06T18:00:00Z", name = "A", certType = "A&P",
            certNumber = "1", voidReason = "Superseded by a corrected entry.",
        )
        assertTrue(e.isVoid)
        assertEquals("Void", e.statusLabel)
        assertTrue(e.body.isNotEmpty())
    }

    @Test
    fun aCustomEntryIsNamedByItsLabel() {
        assertEquals(
            "Weight and balance",
            entry(type = HFLogEntryType.CUSTOM, custom = "Weight and balance").typeLabel,
        )
        // And falls back rather than showing a blank heading.
        assertEquals("Custom", entry(type = HFLogEntryType.CUSTOM, custom = " ").typeLabel)
    }
}
