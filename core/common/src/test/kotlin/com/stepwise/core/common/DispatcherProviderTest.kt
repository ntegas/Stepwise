package com.stepwise.core.common

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherProviderTest {
    @Test
    fun `TestDispatcherProvider runs work deterministically under runTest`() =
        runTest {
            val dispatchers: DispatcherProvider = TestDispatcherProvider()

            val result =
                withContext(dispatchers.io) {
                    1 + 1
                }

            assertEquals(2, result)
        }

    @Test
    fun `StandardDispatcherProvider exposes the real platform dispatchers`() {
        val dispatchers: DispatcherProvider = StandardDispatcherProvider()

        assertEquals(kotlinx.coroutines.Dispatchers.IO, dispatchers.io)
        assertEquals(kotlinx.coroutines.Dispatchers.Default, dispatchers.default)
        assertEquals(kotlinx.coroutines.Dispatchers.Unconfined, dispatchers.unconfined)
    }
}
