package com.hangarflow.app.data.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Roger does with what a mechanic actually says.
 *
 * The table decides what costs money, so it is asserted rather than trusted.
 * Most of these must come back FREE: if "next" or "say that again" started
 * billing, the feature would quietly cost a multiple of what it was costed at,
 * and nothing would look wrong.
 *
 * Phrasings are the ones a person says with their hands inside a cowling, not
 * the ones that are easy to parse.
 */
class RogerIntentTest {

    private fun i(s: String) = HFRoger.intent(s)

    // ── Free: navigation ──────────────────────────────────────────────

    @Test
    fun movingOnIsFree() {
        listOf("next", "next step", "ok next", "done", "finished", "that's complete")
            .forEach {
                assertEquals(HFRogerIntent.Next, i(it), "'$it' should advance")
                assertFalse(i(it).billable, "'$it' must not cost a call")
            }
    }

    @Test
    fun whatsNextAdvancesRatherThanAsking() {
        // Shaped like a question, but nobody saying it wants a paid answer.
        assertEquals(HFRogerIntent.Next, i("what's next"))
        assertFalse(i("what's next").billable)
    }

    @Test
    fun goingBackIsFree() {
        listOf("back", "go back", "previous", "last step", "undo").forEach {
            assertEquals(HFRogerIntent.Back, i(it), "'$it' should go back")
        }
    }

    @Test
    fun repeatingIsFree() {
        listOf("repeat", "say that again", "read it again", "one more time").forEach {
            assertEquals(HFRogerIntent.RepeatStep, i(it), "'$it' should repeat")
            assertFalse(i(it).billable)
        }
    }

    @Test
    fun aSpokenStepNumberIsHeard() {
        // "step for" is what the recogniser hears when someone says "step four".
        assertEquals(HFRogerIntent.ReadStep(4), i("read me step four"))
        assertEquals(HFRogerIntent.ReadStep(4), i("step for"))
        assertEquals(HFRogerIntent.ReadStep(7), i("go to step 7"))
        assertEquals(HFRogerIntent.ReadStep(2), i("step too"))
    }

    @Test
    fun theLocalFactsAreAnsweredLocally() {
        assertEquals(HFRogerIntent.WhereIsIt, i("what page is that"))
        assertEquals(HFRogerIntent.WhereIsIt, i("where is that from"))
        assertEquals(HFRogerIntent.HowLong, i("how long should this take"))
        assertEquals(HFRogerIntent.Tools, i("what tools do i need"))
        listOf("what page is that", "how long should this take", "what tools do i need")
            .forEach { assertFalse(i(it).billable, "'$it' is a local fact and must be free") }
    }

    // ── Billable: a real question ─────────────────────────────────────

    @Test
    fun aGenuineQuestionCosts() {
        val q = i("did you say forty inch pounds or fourteen")
        assertTrue(q is HFRogerIntent.Question)
        assertTrue(q.billable)
    }

    @Test
    fun explainBeatsAgain() {
        // "Explain that again" asks for DIFFERENT words. Re-reading the step
        // verbatim is the one response guaranteed not to help, because the step
        // is exactly what they did not follow.
        val q = i("explain that again")
        assertTrue(q is HFRogerIntent.Question, "got $q")
        assertTrue(q.billable)
        assertTrue(i("say that in plain english") is HFRogerIntent.Question)
        assertTrue(i("what does that mean") is HFRogerIntent.Question)
    }

    @Test
    fun silenceCostsNothing() {
        assertEquals(HFRogerIntent.None, i(""))
        assertEquals(HFRogerIntent.None, i("   ,,,  "))
        assertFalse(i("").billable)
    }

    @Test
    fun mostOfWhatIsSaidIsFree() {
        // The economic claim the feature was costed on, asserted rather than
        // assumed: of the phrasings above, only the real questions bill.
        val said = listOf(
            "next", "next step", "done", "back", "go back", "previous",
            "repeat", "say that again", "one more time", "step four",
            "what page is that", "how long should this take", "what tools do i need",
            "what's next", "undo",
            "did you say forty inch pounds", "explain that again", "what does it mean",
        )
        val billable = said.count { i(it).billable }
        assertEquals(3, billable, "billable set changed: ${said.filter { i(it).billable }}")
    }
}
