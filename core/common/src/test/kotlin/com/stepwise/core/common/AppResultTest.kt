package com.stepwise.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppResultTest {
    @Test
    fun `map transforms a Success value and leaves Failure untouched`() {
        val success: AppResult<Int> = AppResult.Success(2)
        val failure: AppResult<Int> = AppResult.Failure(AppError.Validation("bad input"))

        val mappedSuccess = success.map { it * 10 }
        val mappedFailure = failure.map { it * 10 }

        assertEquals(AppResult.Success(20), mappedSuccess)
        assertIs<AppResult.Failure>(mappedFailure)
        assertEquals("bad input", (mappedFailure).error.message)
    }

    @Test
    fun `onSuccess and onFailure only invoke their matching branch`() {
        var successSeen: Int? = null
        var failureSeen: AppError? = null

        val success: AppResult<Int> = AppResult.Success(5)
        success.onSuccess { successSeen = it }.onFailure { failureSeen = it }

        assertEquals(5, successSeen)
        assertEquals(null, failureSeen)

        successSeen = null
        val failure: AppResult<Int> = AppResult.Failure(AppError.Network("timeout", retryable = true))
        failure.onSuccess { successSeen = it }.onFailure { failureSeen = it }

        assertEquals(null, successSeen)
        assertEquals(AppError.Network("timeout", retryable = true), failureSeen)
    }

    @Test
    fun `each AppError kind carries its own classification`() {
        val errors =
            listOf(
                AppError.Domain("domain rule violated"),
                AppError.Validation("invalid value", field = "targetValue"),
                AppError.Database("constraint failed"),
                AppError.Network("no connection", retryable = true),
                AppError.Auth("session expired"),
                AppError.Sync("conflict", retryable = false),
                AppError.Billing("purchase not verified"),
                AppError.Unexpected("unclassified failure"),
            )

        // Exhaustiveness: every AppError subtype above must map to a distinct, stable label.
        val labels =
            errors.map {
                when (it) {
                    is AppError.Domain -> "domain"
                    is AppError.Validation -> "validation"
                    is AppError.Database -> "database"
                    is AppError.Network -> "network"
                    is AppError.Auth -> "auth"
                    is AppError.Sync -> "sync"
                    is AppError.Billing -> "billing"
                    is AppError.Unexpected -> "unexpected"
                }
            }

        assertEquals(
            listOf("domain", "validation", "database", "network", "auth", "sync", "billing", "unexpected"),
            labels,
        )
    }
}
