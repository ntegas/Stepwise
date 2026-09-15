package com.stepwise.core.common

/**
 * The one error model for the whole app (DEVELOPMENT_PROTOCOL.md rule 18). Any
 * infrastructure exception (a Room `SQLiteException`, a Postgrest HTTP failure, a
 * Supabase auth error, ...) is translated to one of these at the boundary where it's
 * caught — nothing above the repository layer ever sees a raw platform exception type.
 */
sealed class AppError {
    abstract val message: String
    abstract val cause: Throwable?

    /** A business/domain rule was violated (e.g. an operation not valid for the entity's current state). */
    data class Domain(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    /** User input failed validation (e.g. a negative duration, an impossible metric value). */
    data class Validation(
        override val message: String,
        val field: String? = null,
        override val cause: Throwable? = null,
    ) : AppError()

    /** A local persistence (Room) failure. */
    data class Database(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    /** A network/backend call failed. [retryable] distinguishes a transient failure from a permanent one. */
    data class Network(
        override val message: String,
        val retryable: Boolean = true,
        override val cause: Throwable? = null,
    ) : AppError()

    /** Authentication/authorization failed (not signed in, session expired, access denied). */
    data class Auth(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    /** A sync-engine-specific failure (outbox, conflict resolution, upload/download). */
    data class Sync(
        override val message: String,
        val retryable: Boolean = true,
        override val cause: Throwable? = null,
    ) : AppError()

    /** A Google Play Billing-specific failure. */
    data class Billing(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    /** Anything not classifiable above — never presented to the user as-is without a fallback message. */
    data class Unexpected(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()
}

/**
 * A typed alternative to exception-based error propagation for Use Cases/Repositories,
 * so a call site can exhaustively `when` over success vs. every [AppError] kind instead
 * of catching exceptions (module architecture, ARCHITECTURE.md §5: "Result/AppError types").
 */
sealed class AppResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AppResult<T>()

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> =
        when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) action(error)
        return this
    }
}
