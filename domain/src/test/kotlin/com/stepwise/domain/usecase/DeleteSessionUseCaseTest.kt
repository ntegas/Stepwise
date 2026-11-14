package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")

class DeleteSessionUseCaseTest {
    private val sessionRepository = FakeSessionRepository()
    private val useCase = DeleteSessionUseCase(sessionRepository, FakeClock(NOW))

    private suspend fun seedSession(): Session {
        val session =
            Session(
                id = SessionId("session-1"),
                userId = USER,
                sourceType = ExecutableKind.TASK,
                sourceId = "task-1",
                date = LocalDate(2026, 11, 14),
                status = ExecutionStatus.DONE,
                clientEventId = "event-1",
                notes = null,
                sync = SyncMetadata(updatedAt = NOW, version = 2, deletedAt = null),
            )
        sessionRepository.upsertSessionWithMetricValues(session, emptyList())
        return session
    }

    @Test
    fun `delete sets deletedAt rather than removing the row`() =
        runTest {
            seedSession()

            val result = useCase.execute(USER, SessionId("session-1"))

            check(result is AppResult.Success)
            val reloaded = sessionRepository.getById(SessionId("session-1"))
            check(reloaded is AppResult.Success)
            assertNotNull(reloaded.value)
            assertNotNull(reloaded.value?.sync?.deletedAt)
            assertEquals(1, sessionRepository.sessionCount)
        }

    @Test
    fun `delete preserves the Session's existing sync version unchanged`() =
        runTest {
            seedSession()

            useCase.execute(USER, SessionId("session-1"))

            val reloaded = sessionRepository.getById(SessionId("session-1"))
            check(reloaded is AppResult.Success)
            assertEquals(2, reloaded.value?.sync?.version)
        }

    @Test
    fun `deleting a session owned by a different user is rejected`() =
        runTest {
            seedSession()

            val result = useCase.execute(OTHER_USER, SessionId("session-1"))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }
}
