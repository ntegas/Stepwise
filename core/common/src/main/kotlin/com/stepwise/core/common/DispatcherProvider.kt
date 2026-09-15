package com.stepwise.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Centralized coroutine dispatcher access (ARCHITECTURE.md §3: "Dispatchers.IO for
 * Room/network, Dispatchers.Default for calculation work"). Use cases and
 * repositories take a [DispatcherProvider] instead of referencing [Dispatchers]
 * directly, so tests can substitute a deterministic test dispatcher for every
 * dispatcher at once.
 *
 * [main] is included for ViewModel-layer code; on pure JVM (this module's target)
 * no Main dispatcher implementation is on the classpath, so it must not be invoked
 * from a JVM unit test without substituting a fake — only Android/UI code exercises
 * [main] at runtime.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class StandardDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}
