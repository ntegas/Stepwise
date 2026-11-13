package com.stepwise.core.network

import com.stepwise.core.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

/**
 * The one place this app calls supabase-kt's `Auth` plugin (`/DEVELOPMENT_PROTOCOL.md`'s
 * centralization principle) — `:data`'s `AuthRepositoryImpl` depends on this, not on
 * [SupabaseClient] directly, so a supabase-kt API change is a one-file fix here rather than a
 * search-and-replace across every caller.
 *
 * Email/password and Google Sign-In only (`/ARCHITECTURE.md` §11: "via Credential Manager, not
 * the deprecated legacy Google Sign-In SDK") — the Android client obtains a Google ID token
 * through Credential Manager itself (a `:feature:onboarding-auth` concern, not this module's)
 * and hands it to [signInWithGoogleIdToken].
 */
class SupabaseAuthClient(
    private val client: SupabaseClient,
) {
    /**
     * `null` = signed out. Maps supabase-kt's own [SessionStatus] to [UserId] here so no
     * supabase-kt type crosses this module's boundary (`/ARCHITECTURE.md` §5: `:core:network`
     * owns DTOs + mappers to/from `:core:model`).
     *
     * **Real bug found and fixed during DEV-007 review (logic reviewer, CRITICAL)**: an earlier
     * version of this function collapsed [SessionStatus.RefreshFailure] to `null` exactly like
     * [SessionStatus.NotAuthenticated]. supabase-kt's [io.github.jan.supabase.auth.exception.RefreshFailureCause]
     * has exactly two variants — `NetworkError` and `InternalServerError` — neither of which means
     * "this token was genuinely rejected/revoked"; `RefreshFailure` is what a background refresh
     * attempt reports when it simply *couldn't reach the server*, which is the ordinary "opened
     * the app on a plane" case, not a sign-out. Collapsing it to `null` there directly violated
     * `/ARCHITECTURE.md` §11 ("the app should not force a re-login just because it's offline").
     * Fixed with [scan]: only [SessionStatus.NotAuthenticated] clears the last-known [UserId];
     * [SessionStatus.RefreshFailure] and [SessionStatus.Initializing] both preserve whatever was
     * last known, since neither one is itself evidence the session is actually gone.
     */
    fun observeUserId(): Flow<UserId?> =
        client.auth.sessionStatus.scan<SessionStatus, UserId?>(null) { lastKnownUserId, status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.id?.let(::UserId)
                is SessionStatus.NotAuthenticated -> null
                is SessionStatus.RefreshFailure, SessionStatus.Initializing -> lastKnownUserId
            }
        }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
    ) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
    }

    /** Server-side session revocation, not just clearing the local token (`/ARCHITECTURE.md` §11). */
    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun refreshCurrentSession() {
        client.auth.refreshCurrentSession()
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id
}
