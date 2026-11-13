package com.stepwise.data

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure coroutine behavior, no Room/Android dependency exercised — plain JUnit 4, no
// Robolectric runner needed, same reasoning as RecurrencePatternConverterTest in
// :core:database.
class DataErrorMappingTest {
    @Test
    fun `a generic exception is wrapped as AppResult Failure with AppError Database`() =
        runTest {
            val result = runCatchingDatabaseCall<Unit> { throw IllegalStateException("boom") }

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.Database)
        }

    @Test(expected = CancellationException::class)
    fun `a CancellationException is rethrown, not wrapped into a Failure`() =
        runTest {
            runCatchingDatabaseCall<Unit> { throw CancellationException("cancelled") }
        }
}
