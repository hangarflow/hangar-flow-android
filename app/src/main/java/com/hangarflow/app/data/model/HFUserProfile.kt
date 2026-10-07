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
    @SerialName("cert_type") val certType: String? = null
) {
    /** Signing needs both halves — a number with no kind is not a signature. */
    val canSign: Boolean
        get() = !certNumber.isNullOrBlank() && !certType.isNullOrBlank()

    /** An annual or a progressive needs an IA or a repair station. */
    val canSignInspection: Boolean
        get() = canSign && (certType == "A&P/IA" || certType == "Repair Station")
}
