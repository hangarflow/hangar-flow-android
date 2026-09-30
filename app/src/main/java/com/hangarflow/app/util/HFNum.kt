package com.hangarflow.app.util

import java.util.Locale

/**
 * Money and quantity text, for fields the user types into and we parse back.
 *
 * ## Why this exists
 *
 * Every editable number in this app used to be written with
 * `String.format("%.2f", …)` and read back with `toDoubleOrNull()`. Those two
 * disagree on every locale that uses a comma for the decimal point:
 * `String.format` follows `Locale.getDefault()`, while `toDoubleOrNull()` is
 * always POSIX. On a German, French, Italian, Spanish or Portuguese machine the
 * round trip was:
 *
 *     stored $145.00  ->  rendered "145,00"  ->  re-parsed $14,500.00
 *
 * because the old parser also stripped commas as thousands separators. Opening
 * an existing invoice and pressing Save multiplied every line by 100, silently.
 *
 * The locale that governed it was the **device's**, not the language the user
 * picked in Settings, so the app's own language picker never protected anyone.
 *
 * This client authors no invoices, so there was no 100x overcharge here — the
 * damage was quieter. A tech on a German or French phone typing `12,5` hours got
 * `null`, which every call site turned into **0**, so the hours vanished from
 * payroll and from the job. An expense claim typed `23,47` was refused outright
 * and the tech could not submit it at all.
 *
 * ## The contract
 *
 * - **Write** with [field] / [fieldNum] / [fieldHours] — always POSIX, so a
 *   load/save round trip is byte-identical on every machine on earth.
 * - **Read** money with [parseCents] / [parseNum], quantities with [parseQty].
 * - [money] / [display] are read-only.
 *
 * Never call `String.format` or `toDoubleOrNull` directly on a value that makes
 * a round trip through a text field.
 *
 * ## The spec lives in a file, not in this comment
 *
 * `Tools/money_vectors.json` is the specification: every case, with the reason
 * it is there. All THREE codebases execute the same file — this one in
 * `HFNumVectorTest`, Compose Desktop in its own copy of that test, and the Apple
 * repo in `Tools/verify_money_parity.sh`, which extracts its Swift parser from
 * the real source. Change behaviour by changing the vectors first.
 *
 * Kept byte-identical to the Desktop copy apart from the package line;
 * `Tools/verify_hfnum_parity.sh` fails the build if the two diverge.
 *
 * ## Money never touches Double
 *
 * [parseCents] assembles cents with integer arithmetic. `0.145` is 15 cents; via
 * `Math.round(0.145 * 100)` it is 14, because the Double nearest 0.145 is
 * 0.1449999999999999900079881105228. Two audits of this file found real money
 * bugs, and floating point was one of the ways in.
 */
object HFNum {

    /** Units past which an amount is a fat-fingered exponent, not money. */
    private const val MAX_UNITS = 10_000_000_000L

    // ---- writing ----

    /** A money value for an editable field: POSIX, two decimals, no grouping. */
    fun field(cents: Long): String {
        val neg = cents < 0
        val a = Math.abs(cents)
        return (if (neg) "-" else "") + (a / 100).toString() + "." +
            (a % 100).toString().padStart(2, '0')
    }

    /**
     * A plain number for an editable field: POSIX, no trailing zeros, and never
     * scientific notation.
     *
     * `Double.toString(1e11)` is `"1.0E11"`, which [parseQty] then has to accept
     * or the field stops round-tripping. Formatting it plainly keeps write/read
     * symmetric for every value the app can hold.
     */
    fun fieldNum(v: Double): String {
        if (!v.isFinite()) return ""
        if (v == Math.floor(v) && Math.abs(v) < 1e15) return v.toLong().toString()
        return java.math.BigDecimal(v)
            .setScale(6, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    /** Hours for an editable field — two decimals at most, no trailing zeros. */
    fun fieldHours(hours: Double): String {
        if (!hours.isFinite()) return ""
        return java.math.BigDecimal(hours)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    /** Read-only money for display. Deliberately POSIX as well: the app writes
     *  a hard-coded `$`, and a dollar amount grouped the German way ("1.234,56")
     *  beside a `$` sign is neither convention. */
    fun display(cents: Long): String = String.format(Locale.ROOT, "%,.2f", cents / 100.0)

    /** Read-only money with the currency sign — the one every screen wants. */
    fun money(cents: Long): String =
        if (cents < 0) "-$" + display(-cents) else "$" + display(cents)

    // ---- reading ----

    /** The text, split into a sign, digits before the decimal point, and digits
     *  after it. Null when the input is not a number at all. */
    private data class Parts(val negative: Boolean, val whole: String, val frac: String)

    /**
     * Normalise typed text into [Parts]. The rules are enumerated in
     * `Tools/money_vectors.json`; this is their implementation.
     *
     * @param groupThreeDigits how to read a single separator with exactly three
     *   digits after it. Money says true (three decimals is not a legal cent
     *   amount, so `1.234` is one thousand two hundred and thirty-four); a
     *   quantity says false, because `1.234` hours is a real number.
     */
    private fun split(text: String, groupThreeDigits: Boolean): Parts? {
        var s = text.trim()
            .replace("$", "").replace("€", "").replace("£", "")
            .replace("%", "")
            .replace(" ", "").replace(" ", "").replace(" ", "")
            .replace(" ", "")
        if (s.isEmpty()) return null

        var negative = false
        if (s.length >= 2 && s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        } else if (s.startsWith("-")) {
            negative = true
            s = s.substring(1)
        }
        if (s.isEmpty()) return null

        // Only digits and separators may remain. This is what refuses
        // "Infinity", "NaN", "1e999" and a second sign — every one of which is
        // a valid Double and none of which is money somebody typed.
        if (s.any { !it.isDigit() && it != '.' && it != ',' }) return null

        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val dots = s.count { it == '.' }
        val commas = s.count { it == ',' }

        /** Exactly three digits after the only separator, of a value that looks
         *  like a grouped integer: 1–3 leading digits, not starting at zero. So
         *  `1.234` groups to 1234 while `0.500` stays fifty cents. */
        fun looksGrouped(sepAt: Int): Boolean {
            if (!groupThreeDigits) return false
            if (s.length - sepAt - 1 != 3) return false
            val head = s.take(sepAt)
            return head.length in 1..3 && head.isNotEmpty() && !head.startsWith("0")
        }

        // decimalAt = index of the separator that is the decimal point, or -1.
        val decimalAt: Int = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastComma > lastDot) lastComma else lastDot
            commas > 1 -> -1
            dots > 1 -> -1
            commas == 1 -> if (looksGrouped(lastComma)) -1 else lastComma
            dots == 1 -> if (looksGrouped(lastDot)) -1 else lastDot
            else -> -1
        }

        val whole: String
        val frac: String
        if (decimalAt < 0) {
            whole = s.filter { it.isDigit() }
            frac = ""
        } else {
            whole = s.take(decimalAt).filter { it.isDigit() }
            frac = s.substring(decimalAt + 1).filter { it.isDigit() }
        }
        if (whole.isEmpty() && frac.isEmpty()) return null
        return Parts(negative, whole, frac)
    }

    /**
     * Money a human typed, in cents — assembled with integer arithmetic.
     *
     * A third decimal rounds half away from zero and may carry into the dollars,
     * so `"0.999"` is 100 cents rather than 99.
     */
    fun parseCents(text: String): Long? {
        val p = split(text, groupThreeDigits = true) ?: return null
        val whole = p.whole.ifEmpty { "0" }
        // Reject before converting, so an absurd digit string cannot overflow.
        if (whole.trimStart('0').length > 11) return null
        val units = whole.toLongOrNull() ?: return null
        if (units > MAX_UNITS) return null

        var cents = units * 100
        if (p.frac.isNotEmpty()) {
            val two = p.frac.take(2).padEnd(2, '0').toLong()
            val third = if (p.frac.length > 2) p.frac[2] - '0' else 0
            cents += two + (if (third >= 5) 1 else 0)
        }
        return if (p.negative) -cents else cents
    }

    /** Money as a Double, same rules as [parseCents]. */
    fun parseNum(text: String): Double? = parseCents(text)?.let { it / 100.0 }

    /**
     * A quantity or a span of hours — NOT money.
     *
     * Three decimals are legal here (`1.234` hours), so a lone separator is
     * always the decimal point and the grouping rule must not apply.
     */
    fun parseQty(text: String): Double? {
        val p = split(text, groupThreeDigits = false) ?: return null
        val whole = p.whole.ifEmpty { "0" }
        if (whole.trimStart('0').length > 11) return null
        val v = (whole + if (p.frac.isEmpty()) "" else "." + p.frac).toDoubleOrNull() ?: return null
        if (!v.isFinite() || v > MAX_UNITS) return null
        return if (p.negative) -v else v
    }
}
