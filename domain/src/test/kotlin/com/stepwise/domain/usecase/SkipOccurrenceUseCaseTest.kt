package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.OccurrenceIdentity
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeRecurrenceOccurrenceRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")
private val RULE_ID = RecurrenceRuleId("rule-1")
private val DATE = LocalDate(2026, 11, 14)

class SkipOccurrenceUseCaseTest {
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val useCase = SkipOccurrenceUseCase(ruleRepository, occurrenceRepository, FakeClock(NOW))

    private suspend fun seedRule(owner: UserId = USER) {
        ruleRepository.seed(
            RecurrenceRule(
                id = RULE_ID,
                userId = owner,
                ownerType = ExecutableKind.HABIT,
                ownerId = "habit-1",
                pattern = RecurrencePattern.Daily,
                time = null,
                startsOn = LocalDate(2026, 1, 1),
                endsOn = null,
                sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
            ),
        )
    }

    @Test
    fun `skipping a never-materialized occurrence creates a skipped row with no Session`() =
        runTest {
            seedRule()

            val result = useCase.execute(USER, RULE_ID, DATE)

            check(result is AppResult.Success)
            val occurrenceId = OccurrenceIdentity.deterministicId(RULE_ID, DATE)
            val saved = occurrenceRepository.get(occurrenceId)
            assertTrue(saved?.skipped == true)
            assertNull(saved?.sessionId)
        }

    @Test
    fun `skipping an already-skipped occurrence is idempotent, not a second row`() =
        runTest {
            seedRule()

            useCase.execute(USER, RULE_ID, DATE)
            useCase.execute(USER, RULE_ID, DATE)

            val occurrenceId = OccurrenceIdentity.deterministicId(RULE_ID, DATE)
            val saved = occurrenceRepository.get(occurrenceId)
            assertTrue(saved?.skipped == true)
        }

    @Test
    fun `skipping an occurrence that already has a completion is rejected`() =
        runTest {
            seedRule()
            val occurrenceId = OccurrenceIdentity.deterministicId(RULE_ID, DATE)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = occurrenceId,
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = DATE,
                    rescheduledToDate = null,
                    skipped = false,
                    sessionId = SessionId("session-1"),
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )

            val result = useCase.execute(USER, RULE_ID, DATE)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Domain)
        }

    @Test
    fun `skipping a rule owned by a different user is rejected`() =
        runTest {
            seedRule(owner = OTHER_USER)

            val result = useCase.execute(USER, RULE_ID, DATE)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `skipping with a non-existent rule is rejected`() =
        runTest {
            val result = useCase.execute(USER, RecurrenceRuleId("no-such-rule"), DATE)

            check(result is AppResult.Failure)
            assertEquals(AppError.Validation::class, result.error::class)
        }
}
