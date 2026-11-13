package com.stepwise.data

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The one place a raw Room/SQLite exception becomes an [AppError.Database] (`/ARCHITECTURE.md`
 * §4: "nothing above the repository layer ever sees a raw platform exception type") — every
 * repository implementation in this module calls this instead of writing its own try/catch, per
 * `/DEVELOPMENT_PROTOCOL.md`'s centralization principle.
 *
 * A [CancellationException] is only rethrown when it reflects *this coroutine's own* job
 * actually being cancelled ([currentCoroutineContext].[isActive] is false by then) — swallowing
 * a genuine cancellation into a [AppResult.Failure] would break structured-concurrency
 * propagation for the caller. But Room signals "the database was closed mid-call" with a
 * `JobCancellationException` (a [CancellationException] subtype) tied to *Room's own* internal
 * job, not the caller's — in that case this coroutine's own context is still active, and
 * rethrowing it as a plain cancellation would let coroutines' own machinery treat a real failure
 * as an expected, silent cancellation. A real CI test caught this distinction (DEV-006): closing
 * the database mid-call surfaced as an uncaught `JobCancellationException` instead of the
 * expected `AppResult.Failure(AppError.Database)`.
 *
 * The final catch is deliberately broad: Room/SQLite can throw several distinct exception
 * types for the same "this write/read failed" fact (`SQLiteException`, `IllegalStateException`
 * on a closed database, `IllegalArgumentException` on a malformed query, ...) and this
 * function's entire purpose is to map every one of them to the same [AppError.Database]
 * uniformly — narrowing the catch to one exception type would silently let the others fall
 * through unmapped.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runCatchingDatabaseCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        if (!currentCoroutineContext().isActive) throw e
        AppResult.Failure(e.toDatabaseError())
    } catch (e: Exception) {
        AppResult.Failure(e.toDatabaseError())
    }

private fun Exception.toDatabaseError(): AppError.Database =
    AppError.Database(message = message ?: "Local database operation failed", cause = this)
