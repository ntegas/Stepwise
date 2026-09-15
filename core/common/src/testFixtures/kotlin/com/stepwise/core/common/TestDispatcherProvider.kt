package com.stepwise.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A [DispatcherProvider] backed by a single [TestDispatcher] for every role
 * (io/default/main/unconfined), so coroutine-based use cases run synchronously and
 * deterministically under `kotlinx-coroutines-test` without touching real threads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val io: TestDispatcher = dispatcher
    override val default: TestDispatcher = dispatcher
    override val main: TestDispatcher = dispatcher
    override val unconfined: TestDispatcher = dispatcher
}
