package com.stepwise.data

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The network-call sibling of [runCatchingDatabaseCall] — same reasoning, same
 * caller's-own-cancellation-vs-foreign-signal distinction (DEV-006's real CI-caught bug,
 * documented on that function; supabase-kt is built on Ktor/coroutines internally, so the exact
 * same hazard applies here, not a new one to rediscover).
 *
 * **Real bug found and fixed during DEV-007 review (logic reviewer, HIGH)**: an earlier version
 * mapped every [RestException] to [AppError.Auth] unconditionally. supabase-kt's [RestException]
 * hierarchy has four concrete subtypes — [UnauthorizedRestException], [BadRequestRestException],
 * `NotFoundRestException`, and `UnknownRestException` (thrown for every *other* response code,
 * per its own documentation) — so a transient 5xx or a 429 rate-limit from Supabase's Auth API
 * threw the same exception type as a genuine wrong-password rejection, and [AppError.Auth] has
 * no `retryable` field even if it didn't: the distinction was structurally unrepresentable once
 * mapped there, meaning a sign-in attempt during a Supabase outage would be shown identically to
 * an actually-wrong password. Fixed by mapping only [UnauthorizedRestException]/
 * [BadRequestRestException] (a genuine credential/validation rejection) to [AppError.Auth];
 * every other [RestException] (including `UnknownRestException`'s 5xx/429 cases) maps to
 * [AppError.Network] with `retryable = true`.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runCatchingNetworkCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        if (!currentCoroutineContext().isActive) throw e
        AppResult.Failure(e.toNetworkError())
    } catch (e: UnauthorizedRestException) {
        AppResult.Failure(AppError.Auth(message = e.message ?: "Authentication request rejected", cause = e))
    } catch (e: BadRequestRestException) {
        AppResult.Failure(AppError.Auth(message = e.message ?: "Authentication request rejected", cause = e))
    } catch (e: RestException) {
        AppResult.Failure(
            AppError.Network(message = e.message ?: "Network call failed", retryable = true, cause = e),
        )
    } catch (e: Exception) {
        AppResult.Failure(e.toNetworkError())
    }

private fun Exception.toNetworkError(): AppError.Network =
    AppError.Network(message = message ?: "Network call failed", cause = this)
