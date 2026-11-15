package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.RecurrenceRuleRepository

/**
 * Shared ownership check for any Use Case that needs to load a [RecurrenceRule] and verify the
 * caller actually owns it before acting on it — extracted at DEV-010 to close a duplication risk
 * the DEV-010 scoping review flagged: [CompleteOccurrenceUseCase] (DEV-009) already had this
 * exact check inline, and [SkipOccurrenceUseCase] needed the identical one. Two copies of the
 * same ownership logic is exactly what `/DEVELOPMENT_PROTOCOL.md`'s no-duplicated-logic rule
 * exists to prevent, so this is the one place it lives now.
 */
object RecurrenceRuleAccess {
    suspend fun verifyOwnedRule(
        repository: RecurrenceRuleRepository,
        userId: UserId,
        ruleId: RecurrenceRuleId,
    ): AppResult<RecurrenceRule> =
        repository.getById(ruleId).andThen { rule ->
            when {
                rule == null ->
                    AppResult.Failure(AppError.Validation("Recurrence rule not found", field = "recurrenceRuleId"))
                rule.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this recurrence rule"))
                else -> AppResult.Success(rule)
            }
        }
}
