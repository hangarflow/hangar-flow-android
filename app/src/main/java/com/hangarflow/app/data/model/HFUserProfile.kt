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
    @SerialName("updated_at") val updatedAt: String? = null
)
