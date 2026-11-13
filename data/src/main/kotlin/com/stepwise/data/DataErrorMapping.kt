package com.stepwise.data

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import kotlinx.coroutines.CancellationException

/**
 * The one place a raw Room/SQLite exception becomes an [AppError.Database] (`/ARCHITECTURE.md`
 * §4: "nothing above the repository layer ever sees a raw platform exception type") — every
 * repository implementation in this module calls this instead of writing its own try/catch, per
 * `/DEVELOPMENT_PROTOCOL.md`'s centralization principle.
 *
 * [CancellationException] is rethrown, never wrapped: swallowing it into a [AppResult.Failure]
 * would break structured-concurrency cancellation propagation for the caller.
 */
internal suspend fun <T> runCatchingDatabaseCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Database(message = e.message ?: "Local database operation failed", cause = e))
    }
