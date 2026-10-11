package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Port of iOS `HFUserProfile`. Matches `hf_user_profiles` — one row per
 * shop teammate. `authUserId` links back to Supabase auth so the app
 * can resolve "who am I on this device" after sign-in.
 */
@Serializable
data class HFUserProfile(
    val id: String,
    @SerialName("org_id") val orgId: String,
    @SerialName("display_name") val displayName: String = "",
    val initials: String = "",
    val email: String = "",
    val role: String = "tech",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("auth_user_id") val authUserId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /**
     * The certificate this person signs maintenance records with. Android had
     * neither field, so nobody could sign a logbook entry from a tablet — and
     * the gap was invisible, because a missing certificate reads exactly like
     * a person who has not been given one.
     *
     * 14 CFR 43.9(a)(4) requires the number AND the kind. An admin sets them
     * under Users; nothing in the app lets a person type their own, because a
     * typed certificate number is not a signature.
     */
    @SerialName("cert_number") val certNumber: String? = null,
    @SerialName("cert_type") val certType: String? = null,
    /**
     * When the authorisation lapses. macOS has always edited this and both
     * Kotlin clients were missing it, so neither could tell a current
     * certificate from one that expired last year — and an expired
     * authorisation looks exactly like a valid one until someone checks the
     * paper.
     */
    @SerialName("cert_expires_on") val certExpiresOn: String? = null
) {
    /** Signing needs both halves — a number with no kind is not a signature. */
    val canSign: Boolean
        get() = !certNumber.isNullOrBlank() && !certType.isNullOrBlank()

    /** An annual or a progressive needs an IA or a repair station. */
    val canSignInspection: Boolean
        get() = canSign && (certType == "A&P/IA" || certType == "Repair Station")

    /**
     * Nil when there is nothing to worry about; otherwise what to say.
     *
     * Deliberately a WARNING and not a block. The certificate on file is a
     * record of what an admin typed, not the authority itself — refusing a
     * signature because a date in this app lapsed would stop a mechanic who
     * renewed last week and simply has not been updated here. The database
     * decides what may be signed; this tells a human to go and look.
     */
    val certWarning: String?
        get() {
            val on = certExpiresOn?.takeIf { it.isNotBlank() } ?: return null
            val exp = runCatching { java.time.LocalDate.parse(on) }.getOrNull() ?: return null
            val days = java.time.temporal.ChronoUnit.DAYS
                .between(java.time.LocalDate.now(), exp)
            return when {
                days < 0 -> "Authorisation expired ${-days} days ago"
                days <= 60 -> "Authorisation expires in $days days"
                else -> null
            }
        }

}

/**
 * The id every `*_user_id` column in the schema points at.
 *
 * A person has two ids: this profile's `id`, and `authUserId` — the Supabase
 * auth account. Every foreign key in the database references `auth.users`,
 * so the account id is the one that belongs in a `user_id` column. Writing
 * the profile id produces a row that matches no user: the app can't tell it
 * belongs to that person, and the record reads as unattributed.
 *
 * `id` remains the fallback for the handful of profiles that were created
 * with no auth link — a wrong-but-present id beats a null on an audit trail.
 *
 * Use this for anything written to the DATABASE. Comparing one profile to
 * another in the UI should still use `id`.
 */
val HFUserProfile.accountId: String get() = authUserId ?: id
