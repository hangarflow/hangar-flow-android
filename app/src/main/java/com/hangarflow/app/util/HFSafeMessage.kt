package com.hangarflow.app.util

/**
 * Turns a thrown error into something safe to show a mechanic.
 *
 * Supabase-kt puts the **entire failed request** into `Throwable.message` —
 * the URL, the body, and the request headers, which include
 * `Authorization: Bearer <live jwt>` and `apikey`. Every error path in this app
 * used to render that straight into the UI, so a single failed insert printed
 * working credentials on screen — and into any screenshot a shop sent us. (Seen
 * for real: a manual import hit a statement timeout and put the admin's JWT on
 * the Import Manual page.)
 *
 * Keep the first line — that's the actual reason, e.g. "canceling statement due
 * to statement timeout" — and drop the request dump that follows it.
 *
 * PORTED FROM DESKTOP 2026-09-24. Desktop was fixed when the user screenshotted
 * a live JWT on the Import Manual page; Android runs the SAME supabase-kt and
 * had 34 sites rendering `t.message` straight into the UI, so it had the same
 * leak the whole time — on the platform a mechanic is most likely to be holding
 * when they screenshot an error for us.
 */
fun hfSafeMessage(t: Throwable?): String? {
    val raw = t?.message?.takeIf { it.isNotBlank() } ?: return null
    val reason = raw.lineSequence()
        .takeWhile { line ->
            val l = line.trimStart()
            DUMP_PREFIXES.none { l.startsWith(it, ignoreCase = true) }
        }
        .joinToString(" ")
        .trim()
        .ifBlank { "Something went wrong." }

    // Belt and braces: scrub any credential that still slipped through, so a
    // change in how the client formats errors can't reopen this.
    //
    // Inline `[url=https://…]` is Ktor's own format, and for an R2 upload that
    // URL is a *presigned* one — the signature in its query string is a bearer
    // credential for that object, so it can't be shown either. (Seen for real:
    // a 110 MB manual upload timed out and printed the signed PUT URL on the
    // Import Manual page.)
    return reason
        .replace(BEARER, "Bearer ***")
        .replace(APIKEY, "apikey=***")
        .replace(JWT, "***")
        .replace(INLINE_URL, "[url=***]")
        .replace(BARE_URL, "***")
        .take(300)
}

private val DUMP_PREFIXES =
    listOf("URL:", "Headers:", "Http Method:", "Body:", "Request:", "Response:")

private val BEARER = Regex("""Bearer\s+[A-Za-z0-9._\-]+""")
private val APIKEY = Regex("""apikey\s*=\s*\[?[A-Za-z0-9._\-]+""", RegexOption.IGNORE_CASE)
private val JWT = Regex("""eyJ[A-Za-z0-9._\-]{20,}""")
private val INLINE_URL = Regex("""\[url=[^\]]*\]?""", RegexOption.IGNORE_CASE)
private val BARE_URL = Regex("""https?://\S+""")
