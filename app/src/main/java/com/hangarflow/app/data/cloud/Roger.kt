package com.hangarflow.app.data.cloud

/**
 * Roger — the walkthrough's voice, on Android.
 *
 * PORTED VERBATIM from the Apple `HFRoger.swift`, and it has to stay that way.
 * The intent table is the thing that decides what costs money, so the three
 * clients disagreeing about it means the same sentence is free on a phone and
 * billed on a tablet.
 *
 * Android is the platform where this earns its keep. `SpeechRecognizer` and
 * `TextToSpeech` are on-device, cost nothing per use, and the mechanic's hands
 * are full. (Compose Desktop has no speech API at all, which is why Roger is
 * deliberately not on Windows.)
 *
 * The design point worth keeping: **most of what gets said is answered without
 * asking a model.** Navigation, repeats, "what page", "how long", "what tools"
 * are all local. Only a genuine question costs an AI call, and `billable` is
 * the single place that is decided.
 */
sealed interface HFRogerIntent {
    data object Next : HFRogerIntent
    data object Back : HFRogerIntent
    data object RepeatStep : HFRogerIntent
    data class ReadStep(val n: Int) : HFRogerIntent
    /** "what page is that" */
    data object WhereIsIt : HFRogerIntent
    /** The estimate, which is local. */
    data object HowLong : HFRogerIntent
    data object Tools : HFRogerIntent
    /** The only one that costs anything. */
    data class Question(val said: String) : HFRogerIntent
    data object None : HFRogerIntent

    /** True when answering costs an AI call. */
    val billable: Boolean get() = this is Question
}

object HFRoger {

    /**
     * Work out what was said WITHOUT asking a model.
     *
     * Ordered most-specific first: "read me step four" has to beat "step", and
     * "what did you say about the bolt pattern" has to fall through to a
     * question rather than being eaten by the word "about".
     */
    fun intent(said: String): HFRogerIntent {
        val t = said.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.isEmpty()) return HFRogerIntent.None

        // A question mark is gone by now, so questions are spotted by shape.
        val asksSomething = t.startsWith("did ") || t.startsWith("do ") || t.startsWith("does ") ||
            t.startsWith("is ") || t.startsWith("was ") || t.startsWith("should ") ||
            t.startsWith("what ") || t.startsWith("which ") || t.startsWith("why ") ||
            t.startsWith("how do") || t.startsWith("can i") || t.startsWith("are ") ||
            t.contains("correct") || t.contains("right or") || t.contains("you said") ||
            t.contains("you say") || t.contains("explain") || t.contains("mean")

        // Navigation — free. Checked before questions so "what's next" still
        // advances rather than costing a call.
        // `t.endsWith(" next")` is what makes the comment above true. Without
        // it "what's next" starts with "what ", counts as a question, falls
        // past this branch and costs an AI call — the single most common thing
        // anyone says to a walkthrough, billed. "What does next mean" still
        // ends in "mean", so it stays a question.
        if (!asksSomething || t.contains("next step") || t == "next" || t.endsWith(" next")) {
            if (t.contains("next") || t == "done" || t.contains("done next") ||
                t.contains("finished") || t.contains("complete")
            ) return HFRogerIntent.Next
        }
        if (t.contains("go back") || t == "back" || t.contains("previous") ||
            t.contains("last step") || t.contains("undo")
        ) return HFRogerIntent.Back

        // "explain" beats "again". "Explain that again" is a request for
        // DIFFERENT words, not the same ones — answering it by re-reading the
        // step verbatim is the one response guaranteed not to help, since the
        // step is exactly what they did not follow.
        if (t.contains("explain") || t.contains("in plain") ||
            t.contains("what does that mean") || t.contains("what does it mean") ||
            t.contains("simpler")
        ) return HFRogerIntent.Question(said)

        if (t.contains("repeat") || t.contains("again") || t.contains("say that") ||
            t.contains("read that") || t.contains("read it") || t.contains("one more time")
        ) return HFRogerIntent.RepeatStep

        // "read me step four" / "step 4"
        stepNumber(t)?.let { if (t.contains("step")) return HFRogerIntent.ReadStep(it) }

        if (t.contains("what page") || t.contains("which page") ||
            t.contains("where is that") || t.contains("where did that")
        ) return HFRogerIntent.WhereIsIt
        if (t.contains("how long") || t.contains("how much time")) return HFRogerIntent.HowLong
        if (t.contains("what tools") || t.contains("which tools") || t == "tools")
            return HFRogerIntent.Tools

        return if (asksSomething) HFRogerIntent.Question(said) else HFRogerIntent.None
    }

    /**
     * Spoken numbers as well as digits — nobody says "step 4" out loud as a
     * digit, and "step for" is what the recogniser hears.
     */
    private fun stepNumber(t: String): Int? {
        val words = mapOf(
            "one" to 1, "won" to 1, "two" to 2, "too" to 2, "to" to 2, "three" to 3,
            "four" to 4, "for" to 4, "five" to 5, "six" to 6, "seven" to 7,
            "eight" to 8, "ate" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
            "twelve" to 12,
        )
        // Digits across the WHOLE phrase first. Scanning word-by-word and
        // accepting either meant "go to step 7" matched "to" -> 2 and read the
        // wrong step, because the preposition is reached before the number.
        // A spoken number is only consulted when there is no digit at all.
        for (part in t.split(" ")) {
            part.toIntOrNull()?.let { if (it in 1..199) return it }
        }
        for (part in t.split(" ")) {
            words[part]?.let { return it }
        }
        return null
    }
}
