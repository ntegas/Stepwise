package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [setUserId] is the one way a test drives sign-in/sign-out - the sign-up/sign-in/sign-out
 * methods themselves are no-ops a test can call without a real backend.
 */
class FakeAuthRepository(
    initialUserId: UserId? = null,
) : AuthRepository {
    private val userId = MutableStateFlow(initialUserId)

    fun setUserId(id: UserId?) {
        userId.value = id
    }

    override fun observeUserId(): Flow<UserId?> = userId

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun signInWithGoogleIdToken(idToken: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSessionIfNeeded(): AppResult<Unit> = AppResult.Success(Unit)
}
