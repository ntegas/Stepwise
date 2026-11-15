package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.common.FakeClock
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Habit
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.OccurrenceIdentity
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionIdentity
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.TodayAgenda
import com.stepwise.core.model.TodayItemKind
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeRecurrenceOccurrenceRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_000_000)
private val TODAY = LocalDate(2026, 11, 15)
private val YESTERDAY = LocalDate(2026, 11, 14)
private val OWNER = UserId("owner-1")
private val OTHER_USER = UserId("other-1")
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)

private fun task(
    id: String = "task-1",
    date: LocalDate? = TODAY,
    title: String = "Read",
    goalImpactScore: Int? = null,
    userId: UserId = OWNER,
): Task =
    Task(
        id = TaskId(id),
        userId = userId,
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = title,
        date = date,
        time = null,
        deadline = null,
        durationMinutes = null,
        priority = null,
        reminderId = null,
        metricId = null,
        plannedResult = null,
        goalImpactScore = goalImpactScore,
        archived = false,
        createdAt = NOW,
        sync = SYNC,
    )

private fun activity(
    id: String = "activity-1",
    userId: UserId = OWNER,
): Activity =
    Activity(
        id = ActivityId(id),
        userId = userId,
        lifeAreaId = null,
        title = "Running",
        icon = null,
        archived = false,
        sync = SYNC,
    )

private fun habit(
    id: String = "habit-1",
    userId: UserId = OWNER,
): Habit =
    Habit(
        id = HabitId(id),
        userId = userId,
        activityId = null,
        title = "Stretch",
        targetValue = 1.0,
        metricId = MetricId("count"),
        archived = false,
        sync = SYNC,
    )

private fun recurrenceRule(
    ownerType: ExecutableKind,
    ownerId: String,
    id: String = "rule-$ownerId",
    userId: UserId = OWNER,
): RecurrenceRule =
    RecurrenceRule(
        id = RecurrenceRuleId(id),
        userId = userId,
        ownerType = ownerType,
        ownerId = ownerId,
        pattern = RecurrencePattern.Daily,
        time = null,
        startsOn = YESTERDAY,
        endsOn = null,
        sync = SYNC,
    )

private fun oneOffSession(
    sourceType: ExecutableKind,
    sourceId: String,
    date: LocalDate,
    status: ExecutionStatus = ExecutionStatus.DONE,
): Session =
    Session(
        id = SessionIdentity.deterministicId(sourceType, sourceId, date),
        userId = OWNER,
        sourceType = sourceType,
        sourceId = sourceId,
        date = date,
        status = status,
        clientEventId = "event-$sourceId",
        notes = null,
        sync = SYNC,
    )

class GetTodayAgendaUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val sessionRepository = FakeSessionRepository()
    private val resolveOccurrencesUseCase = ResolveOccurrencesUseCase(ruleRepository, occurrenceRepository)
    private val useCase =
        GetTodayAgendaUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            ruleRepository,
            sessionRepository,
            resolveOccurrencesUseCase,
            FakeClock(NOW),
        )

    private suspend fun agenda() = (useCase.execute(OWNER, TimeZone.UTC).first() as AppResult.Success).value

    @Test
    fun `a one-off Task scheduled today appears as a TASK item, not completed`() =
        runTest {
            taskRepository.seed(task())

            val result = agenda()

            assertEquals(1, result.items.size)
            val item = result.items.single()
            assertEquals(TodayItemKind.TASK, item.kind)
            assertEquals(false, item.isCompleted)
            assertEquals(0, result.completedCount)
            assertEquals(1, result.totalCount)
            assertEquals(item, result.focus)
        }

    @Test
    fun `a one-off Task with a recorded Session is completed and has no focus left`() =
        runTest {
            taskRepository.seed(task())
            val session = oneOffSession(ExecutableKind.TASK, "task-1", TODAY)
            sessionRepository.upsertSessionWithMetricValues(session, emptyList())

            val result = agenda()

            assertTrue(result.items.single().isCompleted)
            assertEquals(1, result.completedCount)
            assertNull(result.focus)
        }

    @Test
    fun `an overdue Task with no Session appears in overdue, not in items`() =
        runTest {
            taskRepository.seed(task(date = YESTERDAY))

            val result = agenda()

            assertTrue(result.items.isEmpty())
            assertEquals(1, result.overdue.size)
            assertEquals(TodayItemKind.OVERDUE, result.overdue.single().kind)
        }

    @Test
    fun `an overdue Task already marked DONE is excluded from overdue`() =
        runTest {
            taskRepository.seed(task(date = YESTERDAY))
            sessionRepository.upsertSessionWithMetricValues(
                oneOffSession(ExecutableKind.TASK, "task-1", YESTERDAY, ExecutionStatus.DONE),
                emptyList(),
            )

            val result = agenda()

            assertTrue(result.overdue.isEmpty())
        }

    @Test
    fun `an overdue Task marked CANCELLED is excluded from overdue`() =
        runTest {
            taskRepository.seed(task(date = YESTERDAY))
            sessionRepository.upsertSessionWithMetricValues(
                oneOffSession(ExecutableKind.TASK, "task-1", YESTERDAY, ExecutionStatus.CANCELLED),
                emptyList(),
            )

            val result = agenda()

            assertTrue(result.overdue.isEmpty())
        }

    @Test
    fun `an overdue Task marked MISSED still counts as overdue`() =
        runTest {
            taskRepository.seed(task(date = YESTERDAY))
            sessionRepository.upsertSessionWithMetricValues(
                oneOffSession(ExecutableKind.TASK, "task-1", YESTERDAY, ExecutionStatus.MISSED),
                emptyList(),
            )

            val result = agenda()

            assertEquals(1, result.overdue.size)
        }

    @Test
    fun `a Task with an attached RecurrenceRule ignores its own date field and uses resolved occurrences`() =
        runTest {
            taskRepository.seed(task(date = YESTERDAY))
            ruleRepository.seed(recurrenceRule(ExecutableKind.TASK, "task-1"))

            val result = agenda()

            assertEquals(1, result.items.size)
            assertEquals(TODAY, result.items.single().date)
            assertTrue(result.overdue.isEmpty())
        }

    @Test
    fun `an Activity with a RecurrenceRule appears as an ACTIVITY item today`() =
        runTest {
            activityRepository.seed(activity())
            ruleRepository.seed(recurrenceRule(ExecutableKind.ACTIVITY, "activity-1"))

            val result = agenda()

            assertEquals(1, result.items.size)
            assertEquals(TodayItemKind.ACTIVITY, result.items.single().kind)
        }

    @Test
    fun `a Habit with a RecurrenceRule appears as a HABIT item today`() =
        runTest {
            habitRepository.seed(habit())
            ruleRepository.seed(recurrenceRule(ExecutableKind.HABIT, "habit-1"))

            val result = agenda()

            assertEquals(1, result.items.size)
            assertEquals(TodayItemKind.HABIT, result.items.single().kind)
        }

    @Test
    fun `an Activity with no RecurrenceRule never appears on the agenda`() =
        runTest {
            activityRepository.seed(activity())

            val result = agenda()

            assertTrue(result.items.isEmpty())
            assertTrue(result.overdue.isEmpty())
        }

    @Test
    fun `a skipped recurring occurrence counts as completed`() =
        runTest {
            habitRepository.seed(habit())
            val rule = recurrenceRule(ExecutableKind.HABIT, "habit-1")
            ruleRepository.seed(rule)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(rule.id, TODAY),
                    recurrenceRuleId = rule.id,
                    occurrenceDate = TODAY,
                    rescheduledToDate = null,
                    skipped = true,
                    sessionId = null,
                    sync = SYNC,
                ),
            )

            val result = agenda()

            assertTrue(result.items.single().isCompleted)
        }

    @Test
    fun `ranking orders by goalImpactScore descending and focus picks the first incomplete item`() =
        runTest {
            taskRepository.seed(task(id = "low", title = "Low impact", goalImpactScore = 10))
            taskRepository.seed(task(id = "high", title = "High impact", goalImpactScore = 90))

            val result = agenda()

            assertEquals("High impact", result.items.first().title)
            assertEquals("High impact", result.focus?.title)
        }

    @Test
    fun `an archived Task never appears on the agenda`() =
        runTest {
            taskRepository.seed(task().copy(archived = true))

            val result = agenda()

            assertTrue(result.items.isEmpty())
            assertTrue(result.overdue.isEmpty())
        }

    @Test
    fun `a tombstoned Task never appears on the agenda`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            taskRepository.seed(task().copy(sync = deletedSync))

            val result = agenda()

            assertTrue(result.items.isEmpty())
        }

    @Test
    fun `another user's Tasks never appear on the agenda`() =
        runTest {
            taskRepository.seed(task(userId = OTHER_USER))

            val result = agenda()

            assertTrue(result.items.isEmpty())
            assertTrue(result.overdue.isEmpty())
        }

    @Test
    fun `the agenda recomputes when the underlying Task list changes`() =
        runTest {
            val results = mutableListOf<AppResult<TodayAgenda>>()
            val job = launch { useCase.execute(OWNER, TimeZone.UTC).collect { results.add(it) } }
            runCurrent()
            assertEquals(1, results.size)

            taskRepository.seed(task())
            runCurrent()

            assertEquals(2, results.size)
            job.cancel()
        }

    @Test
    fun `ranking ties on goalImpactScore break by time ascending, then by title`() =
        runTest {
            val later = task(id = "later", title = "Later task").copy(time = LocalTime(hour = 18, minute = 0))
            val earlier = task(id = "earlier", title = "Earlier task").copy(time = LocalTime(hour = 8, minute = 0))
            taskRepository.seed(task(id = "no-time", title = "Zzz no time"))
            taskRepository.seed(later)
            taskRepository.seed(earlier)

            val result = agenda()

            assertEquals(listOf("Earlier task", "Later task", "Zzz no time"), result.items.map { it.title })
        }

    @Test
    fun `an archived Activity with a RecurrenceRule never appears on the agenda`() =
        runTest {
            activityRepository.seed(activity().copy(archived = true))
            ruleRepository.seed(recurrenceRule(ExecutableKind.ACTIVITY, "activity-1"))

            val result = agenda()

            assertTrue(result.items.isEmpty())
        }

    @Test
    fun `a tombstoned Habit with a RecurrenceRule never appears on the agenda`() =
        runTest {
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            habitRepository.seed(habit().copy(sync = deletedSync))
            ruleRepository.seed(recurrenceRule(ExecutableKind.HABIT, "habit-1"))

            val result = agenda()

            assertTrue(result.items.isEmpty())
        }

    @Test
    fun `a tombstoned RecurrenceRule is ignored, so its owner has no agenda item`() =
        runTest {
            habitRepository.seed(habit())
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            ruleRepository.seed(recurrenceRule(ExecutableKind.HABIT, "habit-1").copy(sync = deletedSync))

            val result = agenda()

            assertTrue(result.items.isEmpty())
        }

    @Test
    fun `a tombstoned Session does not count as completing a one-off Task`() =
        runTest {
            taskRepository.seed(task())
            val deletedSync = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = NOW)
            val session = oneOffSession(ExecutableKind.TASK, "task-1", TODAY).copy(sync = deletedSync)
            sessionRepository.upsertSessionWithMetricValues(session, emptyList())

            val result = agenda()

            assertEquals(false, result.items.single().isCompleted)
        }

    @Test
    fun `an overdue Task marked RESCHEDULED still counts as overdue (disclosed gap, see KDoc)`() =
        runTest {
            taskRepository.seed(task(date = YESTERDAY))
            sessionRepository.upsertSessionWithMetricValues(
                oneOffSession(ExecutableKind.TASK, "task-1", YESTERDAY, ExecutionStatus.RESCHEDULED),
                emptyList(),
            )

            val result = agenda()

            assertEquals(1, result.overdue.size)
        }

    @Test
    fun `a recurring item carries its recurrenceRuleId and today's own occurrenceDate`() =
        runTest {
            habitRepository.seed(habit())
            val rule = recurrenceRule(ExecutableKind.HABIT, "habit-1")
            ruleRepository.seed(rule)

            val result = agenda()

            val item = result.items.single()
            assertEquals(rule.id, item.recurrenceRuleId)
            assertEquals(TODAY, item.occurrenceDate)
        }

    @Test
    fun `a recurring item rescheduled into today carries its original occurrenceDate, not today`() =
        runTest {
            habitRepository.seed(habit())
            val rule = recurrenceRule(ExecutableKind.HABIT, "habit-1").copy(endsOn = YESTERDAY)
            ruleRepository.seed(rule)
            occurrenceRepository.seed(
                RecurrenceOccurrence(
                    id = OccurrenceIdentity.deterministicId(rule.id, YESTERDAY),
                    recurrenceRuleId = rule.id,
                    occurrenceDate = YESTERDAY,
                    rescheduledToDate = TODAY,
                    skipped = false,
                    sessionId = null,
                    sync = SYNC,
                ),
            )

            val result = agenda()

            val item = result.items.single()
            assertEquals(rule.id, item.recurrenceRuleId)
            assertEquals(YESTERDAY, item.occurrenceDate)
            assertEquals(TODAY, item.date)
        }

    @Test
    fun `a different time zone shifts which calendar date counts as today`() =
        runTest {
            val lateUtcInstant = Instant.fromEpochMilliseconds(23 * 60 * 60 * 1000L)
            val tzUseCase =
                GetTodayAgendaUseCase(
                    taskRepository,
                    activityRepository,
                    habitRepository,
                    ruleRepository,
                    sessionRepository,
                    resolveOccurrencesUseCase,
                    FakeClock(lateUtcInstant),
                )
            taskRepository.seed(task(date = LocalDate(1970, 1, 2)))

            val resultUtc = (tzUseCase.execute(OWNER, TimeZone.UTC).first() as AppResult.Success).value
            val resultAhead = (tzUseCase.execute(OWNER, TimeZone.of("UTC+12")).first() as AppResult.Success).value

            assertTrue(resultUtc.items.isEmpty() && resultUtc.overdue.isEmpty())
            assertEquals(1, resultAhead.items.size)
        }
}
