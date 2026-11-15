package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")
private val RULE_ID = RecurrenceRuleId("rule-1")

private fun rule(
    pattern: RecurrencePattern,
    owner: UserId = USER,
): RecurrenceRule =
    RecurrenceRule(
        id = RULE_ID,
        userId = owner,
        ownerType = ExecutableKind.HABIT,
        ownerId = "habit-1",
        pattern = pattern,
        time = null,
        startsOn = LocalDate(2026, 1, 1),
        endsOn = null,
        sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
    )

class ResolveOccurrencesUseCaseTest {
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val useCase = ResolveOccurrencesUseCase(ruleRepository, occurrenceRepository)

    private fun seedRule(pattern: RecurrencePattern) {
        ruleRepository.seed(rule(pattern))
    }

    @Test
    fun `a rule with no materialized rows resolves every virtual date, none materialized`() =
        runTest {
            seedRule(RecurrencePattern.Daily)

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Success)
            assertEquals(3, result.value.size)
            assertTrue(result.value.none { it.isMaterialized })
        }

    @Test
    fun `a materialized occurrence overrides its virtual date, not duplicated alongside it`() =
        runTest {
            seedRule(RecurrencePattern.Daily)
            val date = LocalDate(2026, 6, 2)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(RULE_ID, date),
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = date,
                    rescheduledToDate = null,
                    skipped = false,
                    sessionId = SessionId("session-1"),
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Success)
            assertEquals(3, result.value.size)
            val materializedDay = result.value.single { it.occurrenceDate == date }
            assertTrue(materializedDay.isMaterialized)
            assertEquals(SessionId("session-1"), materializedDay.sessionId)
        }

    @Test
    fun `a materialized occurrence outside the rule's current virtual dates is never silently dropped`() =
        runTest {
            // Simulates the rule's own pattern changing after this occurrence already
            // materialized (e.g. a Weekly rule's days edited) - ADR-006: "nothing a user
            // recorded ever silently vanishes."
            val staleDate = LocalDate(2026, 6, 2)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(RULE_ID, staleDate),
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = staleDate,
                    rescheduledToDate = null,
                    skipped = false,
                    sessionId = SessionId("session-1"),
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )
            seedRule(RecurrencePattern.Weekly(setOf(DayOfWeek.MONDAY)))

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Success)
            assertTrue(result.value.any { it.occurrenceDate == staleDate && it.isMaterialized })
        }

    @Test
    fun `a skipped occurrence stays present in the result, flagged skipped rather than hidden`() =
        runTest {
            seedRule(RecurrencePattern.Daily)
            val date = LocalDate(2026, 6, 2)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(RULE_ID, date),
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = date,
                    rescheduledToDate = null,
                    skipped = true,
                    sessionId = null,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Success)
            val skippedDay = result.value.single { it.occurrenceDate == date }
            assertTrue(skippedDay.skipped)
        }

    @Test
    fun `a rescheduled occurrence's displayDate reflects the new date, identity stays on the original`() =
        runTest {
            seedRule(RecurrencePattern.Daily)
            val original = LocalDate(2026, 6, 2)
            val rescheduledTo = LocalDate(2026, 6, 10)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(RULE_ID, original),
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = original,
                    rescheduledToDate = rescheduledTo,
                    skipped = false,
                    sessionId = null,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Success)
            val resolved = result.value.single { it.occurrenceDate == original }
            assertEquals(rescheduledTo, resolved.displayDate)
            assertFalse(resolved.occurrenceDate == resolved.displayDate)
        }

    @Test
    fun `a reschedule moved INTO the queried range is included even though its original date is outside it`() =
        runTest {
            // The direction the DEV-010 review found genuinely missing: occurrenceDate (Jan 1,
            // far outside the queried June range) would previously make this row invisible to
            // any query over the date it was actually moved to.
            seedRule(RecurrencePattern.Daily)
            val original = LocalDate(2026, 1, 1)
            val rescheduledTo = LocalDate(2026, 6, 2)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(RULE_ID, original),
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = original,
                    rescheduledToDate = rescheduledTo,
                    skipped = false,
                    sessionId = null,
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Success)
            val found = result.value.singleOrNull { it.occurrenceDate == original }
            assertTrue(found != null)
            assertEquals(rescheduledTo, found?.displayDate)
            // rescheduledTo (June 2) is itself a day this Daily rule would generate on its own -
            // a real duplicate-row bug found at DEV-014 review (logic reviewer): the rescheduled
            // row and June 2's own ordinary virtual occurrence both displayed at June 2 before the
            // fix. Exactly 3 rows (June 1, the rescheduled one at June 2, June 3), not 4, and no
            // two rows share a displayDate.
            assertEquals(3, result.value.size)
            val displayDates = result.value.map { it.displayDate }
            assertEquals(displayDates.toSet().size, displayDates.size)
        }

    @Test
    fun `an unsupported Custom pattern fails with a Validation error, not a crash or wrong dates`() =
        runTest {
            seedRule(RecurrencePattern.Custom("FREQ=MONTHLY;BYMONTHDAY=1"))

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 30))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `resolving a rule owned by a different user is rejected, never reads its occurrences`() =
        runTest {
            ruleRepository.seed(rule(RecurrencePattern.Daily, owner = OTHER_USER))
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(RULE_ID, LocalDate(2026, 6, 2)),
                    recurrenceRuleId = RULE_ID,
                    occurrenceDate = LocalDate(2026, 6, 2),
                    rescheduledToDate = null,
                    skipped = false,
                    sessionId = SessionId("session-1"),
                    sync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null),
                ),
            )

            val result = useCase.execute(USER, RULE_ID, LocalDate(2026, 6, 1)..LocalDate(2026, 6, 3))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }
}
