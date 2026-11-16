package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.PlanAgenda
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.Task
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.TodayItemKind
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeActivityRepository
import com.stepwise.domain.fake.FakeHabitRepository
import com.stepwise.domain.fake.FakeRecurrenceOccurrenceRepository
import com.stepwise.domain.fake.FakeRecurrenceRuleRepository
import com.stepwise.domain.fake.FakeSessionRepository
import com.stepwise.domain.fake.FakeTaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private val MONDAY = LocalDate(2026, 11, 16)
private val SUNDAY = LocalDate(2026, 11, 22)
private val WEEK = MONDAY..SUNDAY
private val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000)
private val OWNER = UserId("owner-1")
private val SYNC = SyncMetadata(updatedAt = NOW, version = 1, deletedAt = null)

private fun task(
    id: String,
    date: LocalDate?,
): Task =
    Task(
        id = TaskId(id),
        userId = OWNER,
        goalId = null,
        projectId = null,
        lifeAreaId = null,
        title = "Task $id",
        date = date,
        time = null,
        deadline = null,
        durationMinutes = null,
        priority = null,
        reminderId = null,
        metricId = null,
        plannedResult = null,
        goalImpactScore = null,
        archived = false,
        createdAt = NOW,
        sync = SYNC,
    )

class GetPlanRangeUseCaseTest {
    private val taskRepository = FakeTaskRepository()
    private val activityRepository = FakeActivityRepository()
    private val habitRepository = FakeHabitRepository()
    private val ruleRepository = FakeRecurrenceRuleRepository()
    private val occurrenceRepository = FakeRecurrenceOccurrenceRepository()
    private val sessionRepository = FakeSessionRepository()
    private val resolveOccurrencesUseCase = ResolveOccurrencesUseCase(ruleRepository, occurrenceRepository)
    private val useCase =
        GetPlanRangeUseCase(
            taskRepository,
            activityRepository,
            habitRepository,
            ruleRepository,
            sessionRepository,
            resolveOccurrencesUseCase,
        )

    private suspend fun plan(range: ClosedRange<LocalDate> = WEEK): PlanAgenda =
        (useCase.execute(OWNER, range).first() as AppResult.Success).value

    @Test
    fun `a one-off Task inside the range appears only on its own day`() =
        runTest {
            taskRepository.seed(task("task-1", date = LocalDate(2026, 11, 18)))

            val result = plan()

            assertEquals(7, result.days.size)
            val day = result.days.single { it.date == LocalDate(2026, 11, 18) }
            assertEquals(1, day.items.size)
            assertEquals(TodayItemKind.TASK, day.items.single().kind)
            assertTrue(result.days.filter { it.date != LocalDate(2026, 11, 18) }.all { it.items.isEmpty() })
        }

    @Test
    fun `a one-off Task scheduled outside the queried range does not appear at all`() =
        runTest {
            taskRepository.seed(task("task-1", date = MONDAY.minus(1)))

            val result = plan()

            assertTrue(result.days.all { it.items.isEmpty() })
        }

    @Test
    fun `every calendar day in the range gets its own PlanDay, in order, even with nothing scheduled`() =
        runTest {
            val result = plan()

            assertEquals(generateDates(WEEK), result.days.map { it.date })
        }

    @Test
    fun `a Daily recurring Task appears once on every day in the range`() =
        runTest {
            taskRepository.seed(task("task-1", date = null))
            ruleRepository.seed(
                RecurrenceRule(
                    id = RecurrenceRuleId("rule-1"),
                    userId = OWNER,
                    ownerType = ExecutableKind.TASK,
                    ownerId = "task-1",
                    pattern = RecurrencePattern.Daily,
                    time = null,
                    startsOn = MONDAY,
                    endsOn = null,
                    sync = SYNC,
                ),
            )

            val result = plan()

            assertTrue(result.days.all { it.items.size == 1 && it.items.single().kind == TodayItemKind.TASK })
        }

    private fun generateDates(range: ClosedRange<LocalDate>): List<LocalDate> {
        val startEpoch = range.start.toEpochDays()
        val endEpoch = range.endInclusive.toEpochDays()
        return (startEpoch..endEpoch).map { LocalDate.fromEpochDays(it) }
    }

    private fun LocalDate.minus(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() - days)
}
