package com.hangarflow.app.util

/**
 * Aviation inspection-kind detector. Reads a work-log title and
 * returns the canonical tag used to query inspection-checklist
 * references (`hf_manual_references.inspection_kind`).
 *
 * Recognized kinds (in priority order; longer match wins):
 *   - "200hr"   → "200hr"
 *   - "100hr"   → "100hr"
 *   - "50hr"    → "50hr"
 *   - "annual"  → "annual"
 *   - "phase 1" → "phase_1"  ("phase 2" / "phase 3" similarly)
 *   - "pre-buy" / "prebuy" / "pre buy" → "prebuy"
 *
 * Returns null when the title doesn't look like an inspection — the
 * checklist panel hides itself in that case.
 */
object HFInspectionKind {

    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        // "due list" / "due items" are how JetSupport, CAMP, AvTrak,
        // and some smaller aircraft-tracking softwares name what
        // Veryon calls a "FH PKG" — a collection of items due at a
        // given hour interval. A work-log titled "200hr due list"
        // means the same thing as "200hr inspection" so both should
        // light up the checklist panel.
        Regex("""\b200\s*-?\s*(hr|hour|hours|fh)\b""",                    RegexOption.IGNORE_CASE) to "200hr",
        Regex("""\b100\s*-?\s*(hr|hour|hours|fh)\b""",                    RegexOption.IGNORE_CASE) to "100hr",
        Regex("""\b50\s*-?\s*(hr|hour|hours|fh)\b""",                     RegexOption.IGNORE_CASE) to "50hr",
        Regex("""\bannual\s+(inspection|insp|due\s*(list|items?))\b""",  RegexOption.IGNORE_CASE) to "annual",
        Regex("""\bphase\s*1\b""",                                        RegexOption.IGNORE_CASE) to "phase_1",
        Regex("""\bphase\s*2\b""",                                        RegexOption.IGNORE_CASE) to "phase_2",
        Regex("""\bphase\s*3\b""",                                        RegexOption.IGNORE_CASE) to "phase_3",
        Regex("""\bpre[-\s]?buy\b""",                                     RegexOption.IGNORE_CASE) to "prebuy",
        // NAMED packages — recognized so a "Minor Package Inspection" work
        // log lights up its OWN checklist, distinct from the numbered ones.
        Regex("""\bminor\s+package\b""",                                  RegexOption.IGNORE_CASE) to "minor_package",
        Regex("""\bmajor\s+package\b""",                                  RegexOption.IGNORE_CASE) to "major_package",
        Regex("""\bspecial\s+package\b""",                                RegexOption.IGNORE_CASE) to "special_package",
        Regex("""\bconditional\s+package\b""",                            RegexOption.IGNORE_CASE) to "conditional_package",
        Regex("""\bannual\b""",                                           RegexOption.IGNORE_CASE) to "annual"
    )

    fun fromTitle(title: String): String? {
        for ((pattern, kind) in PATTERNS) {
            if (pattern.containsMatchIn(title)) return kind
        }
        return null
    }

    /** Human-readable label for headers like "200hr inspection checklist". */
    fun label(kind: String): String = when (kind) {
        "200hr"   -> "200 hr"
        "100hr"   -> "100 hr"
        "50hr"    -> "50 hr"
        "annual"  -> "Annual"
        "phase_1" -> "Phase 1"
        "phase_2" -> "Phase 2"
        "phase_3" -> "Phase 3"
        "prebuy"  -> "Pre-buy"
        // Named-package slugs ("minor_package") → "Minor Package".
        else      -> kind.split("_").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
