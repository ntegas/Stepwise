package com.stepwise.core.common

/**
 * The one logging abstraction (DEVELOPMENT_PROTOCOL.md rule 44) — no bare `println`
 * anywhere else in the app. This module only defines the contract and severity
 * levels; the concrete production sink (Android `Log`/Crashlytics-backed) is an
 * Android-layer implementation added when :app exists, since that's the earliest
 * point a real platform sink is needed — see ARCHITECTURE.md §26 for the full
 * observability design this contract slots into.
 *
 * Sensitive data (tokens, credentials, unnecessary personal content) is never passed
 * to any [Logger] method — that responsibility sits with every call site, per
 * `docs/security/SECURITY_ARCHITECTURE.md`'s logging security rule.
 */
interface Logger {
    fun debug(
        tag: String,
        message: String,
    )

    fun info(
        tag: String,
        message: String,
    )

    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}
