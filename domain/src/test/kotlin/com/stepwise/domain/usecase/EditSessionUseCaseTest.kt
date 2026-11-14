package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")

class EditSessionUseCaseTest {
    private val sessionRepository = FakeSessionRepository()
    private val useCase = EditSessionUseCase(sessionRepository, FakeClock(NOW))

    private suspend fun seedSession(deletedAt: Instant? = null): Session {
        val session =
            Session(
                id = SessionId("session-1"),
                userId = USER,
                sourceType = ExecutableKind.TASK,
                sourceId = "task-1",
                date = LocalDate(2026, 11, 14),
                status = ExecutionStatus.DONE,
                clientEventId = "event-1",
                notes = "original notes",
                sync = SyncMetadata(updatedAt = NOW, version = 3, deletedAt = deletedAt),
            )
        sessionRepository.upsertSessionWithMetricValues(session, emptyList())
        return session
    }

    @Test
    fun `edit preserves the Session's existing sync version unchanged`() =
        runTest {
            seedSession()
            val command = EditSessionCommand(userId = USER, sessionId = SessionId("session-1"), notes = "corrected")

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals("corrected", result.value.notes)
            assertEquals(3, result.value.sync.version)
        }

    @Test
    fun `editing a session owned by a different user is rejected`() =
        runTest {
            seedSession()
            val command = EditSessionCommand(userId = OTHER_USER, sessionId = SessionId("session-1"), notes = "hacked")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `editing an already-deleted session is rejected`() =
        runTest {
            seedSession(deletedAt = NOW)
            val command = EditSessionCommand(userId = USER, sessionId = SessionId("session-1"), notes = "too late")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }

    @Test
    fun `editing metric values requires an explicit actual, replacing the previous entries`() =
        runTest {
            seedSession()
            val metricId = MetricId("duration_minutes")
            val command =
                EditSessionCommand(
                    userId = USER,
                    sessionId = SessionId("session-1"),
                    metricValues = listOf(MetricEntry(metricId, plannedValue = 30.0, actualValue = 20.0)),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(result.value.id)
            check(metricValues is AppResult.Success)
            assertEquals(20.0, metricValues.value.single().actualValue)
        }

    @Test
    fun `editing a session that does not exist is rejected`() =
        runTest {
            val command = EditSessionCommand(userId = USER, sessionId = SessionId("never-written"), notes = "x")

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }
}
