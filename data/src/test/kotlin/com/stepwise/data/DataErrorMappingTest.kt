package com.stepwise.data

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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

    // The real bug this test guards against (DEV-006, caught by a real CI failure, not
    // invented speculatively): Room signals "the database closed mid-call" with a
    // JobCancellationException tied to Room's own internal job, not this coroutine's. That
    // must be mapped to AppError.Database, same as any other Room failure — not rethrown as
    // if this coroutine itself had been cancelled, which it wasn't.
    @Test
    fun `a CancellationException not caused by this coroutine's own cancellation is wrapped as a Database error`() =
        runTest {
            val result = runCatchingDatabaseCall<Unit> { throw CancellationException("database closed") }

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.Database)
        }

    // The other side of the same check: when this coroutine's own job is genuinely cancelled,
    // the CancellationException must still propagate, not get swallowed into a Failure.
    @Test
    fun `a CancellationException from this coroutine's own cancelled job is rethrown, not wrapped`() =
        runTest {
            var rethrown = false
            val job =
                launch {
                    try {
                        runCatchingDatabaseCall<Unit> {
                            currentCoroutineContext().job.cancel()
                            throw CancellationException("cancelled")
                        }
                    } catch (e: CancellationException) {
                        rethrown = true
                        throw e
                    }
                }
            job.join()

            assertTrue(rethrown)
        }
}
