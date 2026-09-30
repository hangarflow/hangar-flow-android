package com.hangarflow.app.util

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The spec, executed.
 *
 * `Tools/money_vectors.json` is the single source of truth for how typed money
 * and quantities parse. This runs every case in it, under nine locales, and the
 * Apple repo's `Tools/verify_money_parity.sh` runs the same file against the
 * Swift implementation pulled out of its real source. Two clients, one spec,
 * and a divergence is a red test rather than a shop noticing that the same
 * invoice totals differently on the Mac.
 *
 * Both runners assert [VECTORS_SHA256], so editing one copy of the spec and not
 * the other fails loudly instead of quietly un-syncing the platforms.
 */
class HFNumVectorTest {

    private val original = Locale.getDefault()

    @AfterTest fun restore() = Locale.setDefault(original)

    private val locales = listOf(
        Locale.US, Locale.UK, Locale.GERMANY, Locale.FRANCE, Locale.ITALY,
        Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("pt-BR"),
        Locale.CHINA, Locale.forLanguageTag("de-CH"),
    )

    companion object {
        /** Kept in step with the other two repos' copies. Update ALL THREE. */
        const val VECTORS_SHA256 = "6653a1eab1cb75fd5822dde72657ac8c0c4550eb5dd0273e70223e92372921d9"
    }

    private fun vectorsFile(): File {
        // Gradle runs unit tests from the MODULE directory on Android and from the
        // project directory on Compose Desktop, so try both rather than pinning one.
        for (path in listOf("Tools/money_vectors.json", "../Tools/money_vectors.json")) {
            val f = File(path)
            if (f.exists()) return f
        }
        fail("money_vectors.json not found from ${File(".").absolutePath}")
    }

    /** Minimal reader — a JSON library is not worth a dependency for this. */
    private fun cases(section: String, valueKey: String): List<Triple<String, String?, String>> {
        val text = vectorsFile().readText()
        val start = text.indexOf("\"$section\"")
        if (start < 0) fail("section $section missing")
        val open = text.indexOf('[', start)
        var depth = 0
        var end = open
        loop@ for (i in open until text.length) {
            when (text[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { end = i; break@loop } }
            }
        }
        val body = text.substring(open + 1, end)
        val out = mutableListOf<Triple<String, String?, String>>()
        val re = Regex(
            """\{\s*"in"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"$valueKey"\s*:\s*([^,}]+)\s*,\s*"why"\s*:\s*"((?:[^"\\]|\\.)*)"\s*}"""
        )
        for (m in re.findAll(body)) {
            val input = unescape(m.groupValues[1])
            val raw = m.groupValues[2].trim()
            out.add(Triple(input, if (raw == "null") null else raw, unescape(m.groupValues[3])))
        }
        if (out.isEmpty()) fail("no cases parsed from $section")
        return out
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    else -> { sb.append(s[i + 1]); i += 2 }
                }
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    // ---- the spec ----

    @Test fun everyMoneyVectorHoldsInEveryLocale() {
        val cases = cases("money", "cents")
        var checked = 0
        for (loc in locales) {
            Locale.setDefault(loc)
            for ((input, expected, why) in cases) {
                val got = HFNum.parseCents(input)
                val want = expected?.toLong()
                assertEquals(
                    want, got,
                    "parseCents(${q(input)}) under $loc — $why"
                )
                checked++
            }
        }
        println("money vectors: ${cases.size} cases x ${locales.size} locales = $checked assertions")
        assertTrue(cases.size >= 50, "the spec should be thorough; only ${cases.size} money cases")
    }

    @Test fun everyQuantityVectorHoldsInEveryLocale() {
        val cases = cases("qty", "value")
        for (loc in locales) {
            Locale.setDefault(loc)
            for ((input, expected, why) in cases) {
                val got = HFNum.parseQty(input)
                if (expected == null) {
                    assertEquals(null, got, "parseQty(${q(input)}) under $loc — $why")
                } else {
                    val want = expected.toDouble()
                    assertTrue(
                        got != null && Math.abs(got - want) < 1e-9,
                        "parseQty(${q(input)}) under $loc — $why: wanted $want, got $got"
                    )
                }
            }
        }
    }

    /** Write then read, for every cent value in a wide sweep, in every locale.
     *  This is the property the 100x bug violated. */
    @Test fun fieldParseRoundTripsExhaustively() {
        val values = buildList {
            addAll(0L..2_000L)                                  // every cent to $20
            for (v in 2_000L..1_000_000L step 997L) add(v)       // dense to $10k
            for (v in 1_000_000L..999_999_999L step 999_983L) add(v)
            addAll(listOf(9_999_999_999L, 1_000_000_000_000L))
            addAll(this.map { -it })
        }
        for (loc in locales) {
            Locale.setDefault(loc)
            for (cents in values) {
                val onScreen = HFNum.field(cents)
                assertEquals(
                    cents, HFNum.parseCents(onScreen),
                    "round trip broke for $cents under $loc (field rendered \"$onScreen\")"
                )
            }
        }
        println("round trip: ${values.size} amounts x ${locales.size} locales")
    }

    /** A quantity field must round-trip too, including values that used to come
     *  out of `Double.toString` in scientific notation. */
    @Test fun quantityFieldsRoundTrip() {
        val values = listOf(
            0.0, 1.0, 0.5, 21.1, 1.234, 12.5, 100.0, 1e6, 1e10,
            0.01, 999.99, -2.5, 0.333,
        )
        for (loc in locales) {
            Locale.setDefault(loc)
            for (v in values) {
                val onScreen = HFNum.fieldNum(v)
                assertTrue(!onScreen.contains('E') && !onScreen.contains('e'),
                    "fieldNum($v) emitted scientific notation \"$onScreen\" under $loc")
                val back = HFNum.parseQty(onScreen)
                assertTrue(back != null && Math.abs(back - v) < 1e-6,
                    "fieldNum/parseQty broke for $v under $loc (rendered \"$onScreen\", got $back)")
            }
            for (h in listOf(0.0, 21.1, 20.0, 100.0, 1.25, 0.05, 1234.5)) {
                val back = HFNum.parseQty(HFNum.fieldHours(h))
                assertTrue(back != null && Math.abs(back - h) < 1e-9,
                    "fieldHours/parseQty broke for $h under $loc")
            }
        }
    }

    /** Whatever else changes, cents are never assembled through a Double. */
    @Test fun centsAreNotComputedInFloatingPoint() {
        // Each of these is a value where `Math.round(x * 100)` is wrong.
        assertEquals(15L, HFNum.parseCents("0.145"))   // Double gives 14
        assertEquals(115L, HFNum.parseCents("1.15"))   // 114.99999999999999
        assertEquals(87L, HFNum.parseCents("0.87"))    // 86.99999999999999
        assertEquals(29L, HFNum.parseCents("0.29"))    // 28.999999999999996
    }

    @Test fun theSpecFileIsTheOneTheAppleRepoHas() {
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(vectorsFile().readBytes())
            .joinToString("") { "%02x".format(it) }
        println("money_vectors.json sha256 = $sha")
        assertEquals(
            VECTORS_SHA256, sha,
            "money_vectors.json changed. Copy it to the Apple repo and update " +
                "VECTORS_SHA256 in BOTH runners, or the two clients will drift."
        )
    }

    private fun q(s: String) = "\"" + s.replace(" ", "\\u00a0") + "\""
}
