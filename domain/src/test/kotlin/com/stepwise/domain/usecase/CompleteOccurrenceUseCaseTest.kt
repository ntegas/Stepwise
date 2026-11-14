package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.common.FakeIdGenerator
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.OccurrenceIdentity
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionIdentity
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeRecurrenceOccurrenceRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val USER = UserId("user-1")
private val OTHER_USER = UserId("user-2")
private val DATE = LocalDate(2026, 11, 14)
private val RESCHEDULED_DATE = LocalDate(2026, 11, 15)
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)
private val DURATION_METRIC = MetricId("duration_minutes")

class CompleteOccurrenceUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val sessionRepository = FakeSessionRepository()
    private val useCase =
        CompleteOccurrenceUseCase(
            metricResolver = MetricResolver(taskRepository, activityRepository, habitRepository),
            recurrenceRuleRepository = ruleRepository,
            recurrenceOccurrenceRepository = occurrenceRepository,
            sessionRepository = sessionRepository,
            idGenerator = FakeIdGenerator(),
            clock = FakeClock(NOW),
        )

    private fun seedTask(
        plannedResult: Double? = null,
        metricId: MetricId? = null,
    ): Task =
        Task(
            id = TaskId("task-1"),
            userId = USER,
            goalId = null,
            projectId = null,
            lifeAreaId = null,
            title = "Read",
            date = DATE,
            time = null,
            deadline = null,
            durationMinutes = null,
            priority = null,
            reminderId = null,
            metricId = metricId,
            plannedResult = plannedResult,
            goalImpactScore = null,
            archived = false,
            createdAt = NOW,
            sync = SYNC,
        ).also(taskRepository::seed)

    private fun seedRule(
        ruleId: RecurrenceRuleId,
        owner: UserId = USER,
    ): RecurrenceRule =
        RecurrenceRule(
            id = ruleId,
            userId = owner,
            ownerType = ExecutableKind.TASK,
            ownerId = "task-1",
            pattern = RecurrencePattern.Daily,
            time = null,
            startsOn = DATE,
            endsOn = null,
            sync = SYNC,
        ).also(ruleRepository::seed)

    @Test
    fun `double-invoking the same one-off completion resolves to exactly one Session row`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.DONE,
                )

            val first = useCase.execute(command)
            val second = useCase.execute(command)

            check(first is AppResult.Success)
            check(second is AppResult.Success)
            assertEquals(first.value.id, second.value.id)
            assertEquals(1, sessionRepository.sessionCount)
            assertEquals(2, sessionRepository.upsertCallCount)
        }

    @Test
    fun `re-completing the same target preserves its original clientEventId instead of churning it`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.DONE,
                )

            val first = useCase.execute(command)
            val second = useCase.execute(command)

            check(first is AppResult.Success)
            check(second is AppResult.Success)
            assertEquals(first.value.clientEventId, second.value.clientEventId)
        }

    @Test
    fun `DONE defaults a metric's actual value to the Task's planned value when none is given`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(result.value.id)
            check(metricValues is AppResult.Success)
            assertEquals(30.0, metricValues.value.single().actualValue)
        }

    @Test
    fun `DONE with neither an actual nor a planned value is rejected`() =
        runTest {
            seedTask(plannedResult = null, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertEquals(0, sessionRepository.sessionCount)
        }

    @Test
    fun `PARTIAL requires an explicit actual value and never defaults to planned`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.PARTIAL,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertEquals(0, sessionRepository.sessionCount)
        }

    @Test
    fun `PARTIAL succeeds once an explicit actual value is supplied`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.PARTIAL,
                    metricValues = listOf(MetricEntry(DURATION_METRIC, plannedValue = 30.0, actualValue = 15.0)),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(result.value.id)
            check(metricValues is AppResult.Success)
            assertEquals(15.0, metricValues.value.single().actualValue)
        }

    @Test
    fun `RESCHEDULED without rescheduledToDate is rejected before anything is written`() =
        runTest {
            seedTask()
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.RESCHEDULED,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertEquals(0, sessionRepository.sessionCount)
        }

    @Test
    fun `RESCHEDULED with a rescheduledToDate succeeds and stores the new date on the occurrence`() =
        runTest {
            seedTask()
            val ruleId = RecurrenceRuleId("rule-1")
            seedRule(ruleId)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.Recurring(ExecutableKind.TASK, "task-1", DATE, ruleId),
                    status = ExecutionStatus.RESCHEDULED,
                    rescheduledToDate = RESCHEDULED_DATE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val occurrenceId = OccurrenceIdentity.deterministicId(ruleId, DATE)
            assertEquals(RESCHEDULED_DATE, occurrenceRepository.get(occurrenceId)?.rescheduledToDate)
        }

    @Test
    fun `MISSED does not require or resolve any metric value`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.MISSED,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(result.value.id)
            check(metricValues is AppResult.Success)
            assertTrue(metricValues.value.isEmpty())
        }

    @Test
    fun `CANCELLED does not require or resolve any metric value`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.CANCELLED,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(ExecutionStatus.CANCELLED, result.value.status)
        }

    @Test
    fun `IN_PROGRESS is accepted as a valid status with no metric resolution (Timer behavior deferred to DEV-026)`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.IN_PROGRESS,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            assertEquals(ExecutionStatus.IN_PROGRESS, result.value.status)
        }

    @Test
    fun `re-completing with a status that drops a previously-recorded metric tombstones the stale value`() =
        runTest {
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE)
            val done = useCase.execute(CompleteOccurrenceCommand(USER, target, ExecutionStatus.DONE))
            check(done is AppResult.Success)

            val missed = useCase.execute(CompleteOccurrenceCommand(USER, target, ExecutionStatus.MISSED))

            check(missed is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(missed.value.id)
            check(metricValues is AppResult.Success)
            val tombstoned = metricValues.value.single()
            assertTrue(tombstoned.sync.isDeleted)
        }

    @Test
    fun `a Task owned by a different user is rejected, not completed`() =
        runTest {
            seedTask()
            val command =
                CompleteOccurrenceCommand(
                    userId = OTHER_USER,
                    target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
            assertEquals(0, sessionRepository.sessionCount)
        }

    @Test
    fun `a pre-existing Session owned by a different user is not silently overwritten`() =
        runTest {
            seedTask()
            val target = CompletionTarget.OneOff(ExecutableKind.TASK, "task-1", DATE)
            val sessionId = SessionIdentity.deterministicId(target.sourceType, target.sourceId, target.date)
            sessionRepository.upsertSessionWithMetricValues(
                Session(
                    id = sessionId,
                    userId = OTHER_USER,
                    sourceType = target.sourceType,
                    sourceId = target.sourceId,
                    date = target.date,
                    status = ExecutionStatus.DONE,
                    clientEventId = "preexisting-event",
                    notes = "someone else's session",
                    sync = SYNC,
                ),
                metricValues = emptyList(),
            )

            val result = useCase.execute(CompleteOccurrenceCommand(USER, target, ExecutionStatus.DONE))

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
        }

    @Test
    fun `a Habit completion defaults its actual value to the Habit's own targetValue`() =
        runTest {
            val habit =
                Habit(
                    id = HabitId("habit-1"),
                    userId = USER,
                    activityId = null,
                    title = "Meditate",
                    targetValue = 15.0,
                    metricId = DURATION_METRIC,
                    archived = false,
                    sync = SYNC,
                )
            habitRepository.seed(habit)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.HABIT, "habit-1", DATE),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(result.value.id)
            check(metricValues is AppResult.Success)
            assertEquals(15.0, metricValues.value.single().actualValue)
        }

    @Test
    fun `an Activity completion uses only the caller's own metric entries, with no entity-level default`() =
        runTest {
            val activity =
                Activity(
                    id = ActivityId("activity-1"),
                    userId = USER,
                    lifeAreaId = null,
                    title = "Running",
                    icon = null,
                    archived = false,
                    sync = SYNC,
                )
            activityRepository.seed(activity)
            val distanceMetric = MetricId("distance_km")
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.OneOff(ExecutableKind.ACTIVITY, "activity-1", DATE),
                    status = ExecutionStatus.DONE,
                    metricValues =
                        listOf(
                            MetricEntry(DURATION_METRIC, plannedValue = null, actualValue = 45.0),
                            MetricEntry(distanceMetric, plannedValue = null, actualValue = 7.5),
                        ),
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val metricValues = sessionRepository.getMetricValuesForSession(result.value.id)
            check(metricValues is AppResult.Success)
            assertEquals(2, metricValues.value.size)
        }

    @Test
    fun `a recurring completion materializes the occurrence and links its sessionId`() =
        runTest {
            seedTask()
            val ruleId = RecurrenceRuleId("rule-1")
            seedRule(ruleId)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.Recurring(ExecutableKind.TASK, "task-1", DATE, ruleId),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Success)
            val occurrenceId = OccurrenceIdentity.deterministicId(ruleId, DATE)
            val occurrence = occurrenceRepository.get(occurrenceId)
            assertEquals(result.value.id, occurrence?.sessionId)
        }

    @Test
    fun `a recurring completion is rejected when the rule belongs to a different user`() =
        runTest {
            seedTask()
            val ruleId = RecurrenceRuleId("rule-1")
            seedRule(ruleId, owner = OTHER_USER)
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.Recurring(ExecutableKind.TASK, "task-1", DATE, ruleId),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Auth)
            assertEquals(0, sessionRepository.sessionCount)
        }

    @Test
    fun `a recurring completion is rejected when the rule does not exist`() =
        runTest {
            seedTask()
            val missingRuleId = RecurrenceRuleId("no-such-rule")
            val command =
                CompleteOccurrenceCommand(
                    userId = USER,
                    target = CompletionTarget.Recurring(ExecutableKind.TASK, "task-1", DATE, missingRuleId),
                    status = ExecutionStatus.DONE,
                )

            val result = useCase.execute(command)

            check(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `re-invoking for the same occurrence twice (sequential, one store) converges on one Session by construction`() =
        runTest {
            // This proves SessionIdentity's determinism end to end through the use case - it does
            // NOT prove safety under genuine concurrency or cross-device divergence (both calls
            // run sequentially against one in-memory fake); that is tracked separately, not by
            // this test, per the DEV-009 review (logic reviewer) that renamed this test from an
            // earlier, overclaiming name.
            seedTask(plannedResult = 30.0, metricId = DURATION_METRIC)
            val ruleId = RecurrenceRuleId("rule-1")
            seedRule(ruleId)
            val target = CompletionTarget.Recurring(ExecutableKind.TASK, "task-1", DATE, ruleId)

            val first = useCase.execute(CompleteOccurrenceCommand(USER, target, ExecutionStatus.DONE))
            val second = useCase.execute(CompleteOccurrenceCommand(USER, target, ExecutionStatus.DONE))

            check(first is AppResult.Success)
            check(second is AppResult.Success)
            assertEquals(first.value.id, second.value.id)
            assertEquals(1, sessionRepository.sessionCount)
        }
}
