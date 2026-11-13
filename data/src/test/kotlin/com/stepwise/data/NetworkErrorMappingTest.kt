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

// Pure coroutine behavior, no Android/supabase-kt runtime exercised — plain JUnit 4, same
// reasoning as DataErrorMappingTest (its direct sibling: this is the exact cancellation-safety
// pattern DEV-006 found and fixed there, reused here per runCatchingNetworkCall's own KDoc).
//
// Deliberately NOT covered here: the RestException-subtype branching
// (UnauthorizedRestException/BadRequestRestException -> AppError.Auth vs. every other
// RestException -> AppError.Network, the DEV-007 review fix in runCatchingNetworkCall). This
// sandbox has no Android SDK and cannot resolve/compile against the real supabase-kt dependency
// (same standing limitation as every Android-only module since DEV-003), so constructing a real
// RestException subtype here could not be verified to even compile, let alone behave as claimed.
// Tracked as VERIFICATION DEBT — CI is the environment that can both compile this module and
// actually exercise those branches against real supabase-kt types.
class NetworkErrorMappingTest {
    @Test
    fun `a generic exception is wrapped as AppResult Failure with AppError Network`() =
        runTest {
            val result = runCatchingNetworkCall<Unit> { throw IllegalStateException("boom") }

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.Network)
        }

    @Test
    fun `a CancellationException not caused by this coroutine's own cancellation is wrapped as a Network error`() =
        runTest {
            val result = runCatchingNetworkCall<Unit> { throw CancellationException("connection reset") }

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.Network)
        }

    @Test
    fun `a CancellationException from this coroutine's own cancelled job is rethrown, not wrapped`() =
        runTest {
            var rethrown = false
            val job =
                launch {
                    try {
                        runCatchingNetworkCall<Unit> {
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
