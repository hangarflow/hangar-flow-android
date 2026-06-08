package com.hangarflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the org's append-only audit log (the paper trail) — who did
 * what, when. Decodes `hf_audit_log` directly.
 */
@Serializable
data class HFAuditEvent(
    val id: String,
    @SerialName("org_id") val orgId: String = "",
    @SerialName("entity_type") val entityType: String = "",
    @SerialName("entity_id") val entityId: String? = null,
    val action: String = "",
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("actor_name") val actorName: String = "",
    val summary: String = "",
    @SerialName("created_at") val createdAt: String? = null
)
