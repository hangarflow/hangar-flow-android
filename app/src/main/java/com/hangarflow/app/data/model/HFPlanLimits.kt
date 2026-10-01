package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the shop's plan allows, and how much of it is used.
 *
 * The caps are enforced in Postgres by triggers (see the plan-limits
 * migration), so nothing here is load-bearing for security. It exists so a
 * tech finds out BEFORE filling in a form that the shop is out of seats,
 * instead of after pressing Save and being handed a database error.
 *
 * A null max means unlimited, matching the database: no plan recorded, or a
 * plan with no number against it, is never treated as zero.
 */
@Serializable
data class HFPlanLimitRow(
    val plan: String = "",
    val label: String = "",
    @SerialName("max_techs") val maxTechs: Int? = null,
    @SerialName("max_planes") val maxPlanes: Int? = null,
    @SerialName("max_manuals") val maxManuals: Int? = null,
    @SerialName("max_ai_calls_month") val maxAiCalls: Int? = null,
)

data class HFPlanStatus(
    val plan: String? = null,
    val label: String = "",
    val maxTechs: Int? = null,
    val techsUsed: Int = 0,
    val maxPlanes: Int? = null,
    val planesUsed: Int = 0,
    val maxAiCalls: Int? = null,
    val aiUsed: Int = 0,
) {
    val techsUnlimited: Boolean get() = maxTechs == null
    val planesUnlimited: Boolean get() = maxPlanes == null
    val aiUnlimited: Boolean get() = maxAiCalls == null

    val canAddTech: Boolean get() = maxTechs == null || techsUsed < maxTechs!!
    val canAddPlane: Boolean get() = maxPlanes == null || planesUsed < maxPlanes!!

    val techsRemaining: Int? get() = maxTechs?.let { (it - techsUsed).coerceAtLeast(0) }
    val planesRemaining: Int? get() = maxPlanes?.let { (it - planesUsed).coerceAtLeast(0) }
    val aiRemaining: Int? get() = maxAiCalls?.let { (it - aiUsed).coerceAtLeast(0) }

    /** "2 of 3 used" / "4 technicians" when uncapped. Null when there is nothing worth saying. */
    fun techsCaption(): String? =
        maxTechs?.let { "$techsUsed of $it technician seats used" }
    fun planesCaption(): String? =
        maxPlanes?.let { "$planesUsed of $it aircraft used" }
    fun aiCaption(): String? =
        maxAiCalls?.let { "$aiUsed of $it AI actions used this month" }

    companion object { val unlimited = HFPlanStatus() }
}

/**
 * Turns the trigger's refusal into something a mechanic can act on.
 *
 * The database raises, with errcode 23514:
 *   HF_PLAN_LIMIT: this plan allows 3 aircraft. You have 3 already.
 *
 * Without this it reaches the screen through the generic check-constraint
 * branch as "One of those values isn't allowed.", which tells the user
 * nothing and sounds like a bug in the app.
 */
object HFPlanLimitError {
    private val detail = Regex("""allows\s+(\d+)\s+([a-z ]+?)\.""", RegexOption.IGNORE_CASE)

    fun friendlyOrNull(raw: String?): String? {
        val text = raw ?: return null
        if (!text.contains("HF_PLAN_LIMIT", ignoreCase = true)) return null
        val m = detail.find(text)
        val what = m?.groupValues?.getOrNull(2)?.trim()
        val n = m?.groupValues?.getOrNull(1)
        return if (n != null && !what.isNullOrBlank())
            "Your plan includes $n $what and they're all in use. Upgrade to add more."
        else
            "You've reached a limit on your current plan. Upgrade to add more."
    }
}

/** Raised when an AI edge function answers 429 because the shop is out of AI actions. */
class HFAiQuotaException(
    val limit: Int?,
    val used: Int?,
    message: String,
) : Exception(message)

/**
 * Recognises the quota refusal an AI edge function returns.
 *
 * This matters more than it looks. Every AI decoder in the cloud service uses
 * `ignoreUnknownKeys` over a result type whose fields are defaulted, so the
 * 429 body — {"error": …, "code": "ai_quota_exceeded"} — decodes perfectly
 * well into an EMPTY result. Without this check the tech presses Search, waits,
 * and gets a blank answer with nothing to explain it.
 */
object HFAiQuota {
    @Serializable
    private data class Body(
        val error: String? = null,
        val code: String? = null,
        val limit: Int? = null,
        val used: Int? = null,
    )

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Throws [HFAiQuotaException] when [status]/[body] are a quota refusal. */
    fun throwIfExceeded(status: Int, body: String) {
        if (status != 429) return
        val parsed = runCatching { json.decodeFromString(Body.serializer(), body) }.getOrNull()
        if (parsed?.code != null && parsed.code != "ai_quota_exceeded") return
        val msg = parsed?.error?.takeIf { it.isNotBlank() }
            ?: "This plan's AI actions for the month are used up. Upgrade to Core for unlimited."
        throw HFAiQuotaException(parsed?.limit, parsed?.used, msg)
    }
}
