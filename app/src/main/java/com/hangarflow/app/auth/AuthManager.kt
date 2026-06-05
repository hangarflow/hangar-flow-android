package com.hangarflow.app.auth

import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Kotlin port of iOS `AuthManager.swift`. Singleton scope matches the iOS
 * behavior where one auth manager owns the session for the whole process.
 *
 * `state` is a StateFlow that the login screen and signed-in shell observe.
 * Supabase-kt's session persistence handles the keychain/DataStore work
 * under the hood — we don't re-implement token storage.
 */
object AuthManager {
    private val client get() = SupabaseClientProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AuthState.initial())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private const val PREFERRED_ORG_NAME = "Hangar Flow"

    init {
        // Observe session transitions (signed-in / signed-out / token refresh)
        // so the UI reacts the instant Supabase restores or drops a session.
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _state.update { it.copy(isSignedIn = true, loading = false, error = null) }
                        loadMembership()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        SharedStore.clear()
                        _state.update {
                            AuthState.initial().copy(loading = false)
                        }
                    }
                    is SessionStatus.Initializing -> {
                        _state.update { it.copy(loading = true) }
                    }
                    is SessionStatus.RefreshFailure -> {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = "Session refresh failed — please sign in again."
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * UI-friendly wrappers — launch on the manager's long-lived scope so
     * callers don't have to manage Composable-scoped coroutines that get
     * cancelled mid-flight when the login screen unmounts on success.
     */
    fun signIn(email: String, password: String) {
        scope.launch { signInInternal(email, password) }
    }

    fun signOut() {
        scope.launch { signOutInternal() }
    }

    sealed class UpdateResult {
        object Success : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    /**
     * Change password for the signed-in user. Mirrors the Desktop flow —
     * no re-auth required by Supabase, but the UI should still confirm
     * the user typed the new one twice.
     */
    suspend fun updatePassword(newPassword: String): UpdateResult {
        return try {
            client.auth.updateUser { password = newPassword }
            UpdateResult.Success
        } catch (t: Throwable) {
            UpdateResult.Error(friendlyError(t))
        }
    }

    /**
     * Change email for the signed-in user. Supabase emails a confirmation
     * link to the new address — the change only takes effect once the
     * user clicks it. UI should make the pending state obvious.
     */
    suspend fun updateEmail(newEmail: String): UpdateResult {
        val trimmed = newEmail.trim()
        if (!trimmed.contains("@")) return UpdateResult.Error("Enter a valid email address.")
        return try {
            client.auth.updateUser { email = trimmed }
            UpdateResult.Success
        } catch (t: Throwable) {
            UpdateResult.Error(friendlyError(t))
        }
    }

    private suspend fun signInInternal(email: String, password: String) {
        _state.update { it.copy(loading = true, error = null) }
        try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            // Session status collector (above) will flip isSignedIn + trigger
            // loadMembership. No need to do it again here.
        } catch (t: Throwable) {
            _state.update { it.copy(loading = false, error = friendlyError(t)) }
        }
    }

    private suspend fun signOutInternal() {
        _state.update { it.copy(loading = true, error = null) }
        try {
            client.auth.signOut()
        } catch (t: Throwable) {
            _state.update { it.copy(loading = false, error = t.message) }
        }
    }

    private suspend fun loadMembership() {
        val userId = client.auth.currentUserOrNull()?.id ?: return
        try {
            val memberships = client.postgrest
                .from("memberships")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("org_id", "role")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<MembershipRow>()

            val organizations = client.postgrest
                .from("organizations")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id", "name", "created_at"))
                .decodeList<OrganizationRow>()

            val chosen = preferredMembership(memberships, organizations)
            if (chosen != null) {
                val orgRow = organizations.firstOrNull { it.id == chosen.org_id }
                val orgName = orgRow?.name?.trim()?.ifEmpty { null } ?: PREFERRED_ORG_NAME
                _state.update {
                    it.copy(
                        orgId = chosen.org_id,
                        orgName = orgName,
                        role = chosen.role,
                        loading = false
                    )
                }
                // Kick off the data pull for this org. SharedStore short-
                // circuits on duplicate bootstraps for the same org id, so
                // this is safe to call on every auth event.
                SharedStore.bootstrap(chosen.org_id)
            } else {
                _state.update {
                    it.copy(
                        orgId = null,
                        orgName = PREFERRED_ORG_NAME,
                        role = "tech",
                        loading = false
                    )
                }
            }
        } catch (t: Throwable) {
            // Fail closed (tech) if anything goes wrong
            _state.update {
                it.copy(
                    orgId = null,
                    orgName = PREFERRED_ORG_NAME,
                    role = "tech",
                    loading = false
                )
            }
        }
    }

    private fun preferredMembership(
        memberships: List<MembershipRow>,
        organizations: List<OrganizationRow>
    ): MembershipRow? {
        if (memberships.isEmpty()) return null
        val byOrg = memberships.associateBy { it.org_id }
        val matching = organizations.filter { byOrg[it.id] != null }

        val preferred = matching.firstOrNull {
            it.name.trim().equals(PREFERRED_ORG_NAME, ignoreCase = true)
        }
        if (preferred != null) return byOrg[preferred.id]

        val newest = matching.maxByOrNull { it.created_at ?: "" }
        if (newest != null) return byOrg[newest.id]

        return memberships.first()
    }

    private fun friendlyError(t: Throwable): String {
        val msg = (t.message ?: "").lowercase()
        return when {
            "invalid login credentials" in msg
                || "invalid_credentials" in msg
                || "user not found" in msg ->
                "No account found. Contact your administrator for access."
            "email not confirmed" in msg ->
                "Your email hasn't been confirmed yet. Check your inbox for the invite link."
            else -> t.message ?: "Sign-in failed. Please try again."
        }
    }

    @Serializable
    private data class MembershipRow(val org_id: String, val role: String)

    @Serializable
    private data class OrganizationRow(
        val id: String,
        val name: String,
        val created_at: String? = null
    )
}

data class AuthState(
    val isSignedIn: Boolean,
    val loading: Boolean,
    val error: String?,
    val orgId: String?,
    val orgName: String,
    val role: String
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
    val isLeadTech: Boolean get() = role.equals("lead_tech", ignoreCase = true)
    val roleLabel: String get() = when {
        isAdmin -> "Admin"; isLeadTech -> "Lead Tech"; else -> "Tech"
    }

    // Lead techs get the shop-floor admin powers on mobile EXCEPT file
    // import (laptop-only), payroll, plane delete/archive, org settings, and
    // time-off approval. Mirrors the macOS/Compose/iOS capability model.
    val canEditCalendar: Boolean get() = isAdmin || isLeadTech       // arrivals, deadlines, events
    val canManageMembers: Boolean get() = isAdmin || isLeadTech      // add/delete users (never admins)
    val canManageWorkLogs: Boolean get() = isAdmin || isLeadTech     // add work logs
    val canEditPlaneSchedule: Boolean get() = isAdmin || isLeadTech  // add plane + edit schedule
    val canSeeParts: Boolean get() = isAdmin || isLeadTech           // inventory + parts to order
    val canSeeLiveBoard: Boolean get() = isAdmin || isLeadTech       // admin live view + cards
    val canImportFiles: Boolean get() = isAdmin                      // NEVER lead tech on mobile

    companion object {
        fun initial() = AuthState(
            isSignedIn = false,
            loading = true,
            error = null,
            orgId = null,
            orgName = "Hangar Flow",
            role = "tech"
        )
    }
}
