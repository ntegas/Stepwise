package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.SessionRepository

/**
 * The read half of "edit/delete recompute" (`/docs/dev-plan-detailed.md` DEV-018's own completion
 * check) - loads an existing Session's own [SessionMetricValue] rows so a correction UI can show
 * what's actually stored before the user edits it, the same "editing is a correction of what's
 * already there, never a fresh guess" reasoning [EditSessionUseCase] itself already states.
 *
 * Not found and not owned read back identically (`Success(emptyList())`), the same non-disclosure
 * convention [GoalReadUseCases.kt]'s own read-side Use Cases already established.
 */
class GetSessionMetricValuesUseCase(
    private val sessionRepository: SessionRepository,
) {
    suspend fun execute(
        userId: UserId,
        sessionId: SessionId,
    ): AppResult<List<SessionMetricValue>> =
        sessionRepository.getById(sessionId).andThen { session ->
            if (session == null || session.userId != userId) {
                AppResult.Success(emptyList())
            } else {
                sessionRepository.getMetricValuesForSession(sessionId)
            }
        }
}
