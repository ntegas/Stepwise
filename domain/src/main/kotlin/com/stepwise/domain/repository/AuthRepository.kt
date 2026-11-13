package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * Authentication/session state (`/ARCHITECTURE.md` §11, DEV-007, security level **S3**).
 * Deliberately a separate interface from every other `*Repository` in this package: those are
 * all repositories *for* a [UserId] (local-first reads/writes scoped to an already-known owner);
 * this one is what *establishes* that owner in the first place, so it has no shared shape with
 * them to reuse.
 *
 * [observeUserId] is the one source every other repository's caller uses to know "is anyone
 * signed in, and who" — `null` means signed out. A previously-authenticated session keeps
 * working offline from its cached token (`/ARCHITECTURE.md` §11: "the app should not force a
 * re-login just because it's offline") — this interface doesn't need a separate
 * "am I online" signal for that reason; the implementation's token cache already handles it.
 */
interface AuthRepository {
    fun observeUserId(): Flow<UserId?>

    suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit>

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit>

    /** [idToken] comes from Android Credential Manager (`/ARCHITECTURE.md` §11), not this layer. */
    suspend fun signInWithGoogleIdToken(idToken: String): AppResult<Unit>

    /** Revokes the session server-side — not just clearing the local token (`/ARCHITECTURE.md` §11). */
    suspend fun signOut(): AppResult<Unit>

    /** Silent refresh on app start (`/ARCHITECTURE.md` §11) — a no-op if the session is still fresh. */
    suspend fun refreshSessionIfNeeded(): AppResult<Unit>
}
