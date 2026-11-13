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
// Deliberately broad: Room/SQLite can throw several distinct exception types for the same
// "this write/read failed" fact (SQLiteException, IllegalStateException on a closed
// database, IllegalArgumentException on a malformed query, ...) and this function's entire
// purpose is to map every one of them to the same AppError.Database uniformly — narrowing
// the catch to one exception type would silently let the others fall through unmapped.
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runCatchingDatabaseCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Database(message = e.message ?: "Local database operation failed", cause = e))
    }
