package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.UserId
import com.stepwise.core.network.SupabaseAuthClient
import com.stepwise.data.runCatchingNetworkCall
import com.stepwise.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class AuthRepositoryImpl(
    private val authClient: SupabaseAuthClient,
) : AuthRepository {
    override fun observeUserId(): Flow<UserId?> = authClient.observeUserId()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit> = runCatchingNetworkCall { authClient.signUpWithEmail(email, password) }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit> = runCatchingNetworkCall { authClient.signInWithEmail(email, password) }

    override suspend fun signInWithGoogleIdToken(idToken: String): AppResult<Unit> =
        runCatchingNetworkCall { authClient.signInWithGoogleIdToken(idToken) }

    override suspend fun signOut(): AppResult<Unit> = runCatchingNetworkCall { authClient.signOut() }

    override suspend fun refreshSessionIfNeeded(): AppResult<Unit> =
        runCatchingNetworkCall { authClient.refreshCurrentSession() }
}
