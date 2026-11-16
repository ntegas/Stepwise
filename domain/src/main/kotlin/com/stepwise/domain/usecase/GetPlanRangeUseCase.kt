package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Activity
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Habit
import com.stepwise.core.model.PlanAgenda
import com.stepwise.core.model.PlanDay
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionIdentity
import com.stepwise.core.model.Task
import com.stepwise.core.model.TodayItem
import com.stepwise.core.model.TodayItemKind
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The Plan/Calendar engine (`/ROADMAP.md` DEV-017) — [GetTodayAgendaUseCase]'s own sibling,
 * generalized from "today" to an arbitrary [LocalDate] `range`, per `/ARCHITECTURE.md` §13's
 * "Today and Plan/Calendar call the same occurrence-resolution function." The recurring-item
 * resolution itself ([resolveScheduledItems]) is shared verbatim with [GetTodayAgendaUseCase],
 * not re-derived, so a future fix to that logic's own bug history (DEV-010/DEV-014) only needs
 * applying once. What differs from Today is the *shape* of the result: Today reduces one day to
 * `focus`/`items`/`overdue`/counts; Plan buckets every day in [range] into its own [PlanDay],
 * with no separate Overdue concept (an Overdue Task elsewhere in the queried range is simply that
 * day's own [PlanDay.items] entry, same as any other day).
 *
 * **A recurring owner's [Task.date] is ignored exactly like [GetTodayAgendaUseCase] already
 * decided** — a Task with a live [RecurrenceRule] is fully recurrence-driven; the rule's resolved
 * occurrences are its only source of dates here too, for the identical reasoning stated in that
 * class's own KDoc, not re-derived or re-argued here.
 */
class GetPlanRangeUseCase(
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
    private val recurrenceRuleRepository: RecurrenceRuleRepository,
    private val sessionRepository: SessionRepository,
    private val resolveOccurrencesUseCase: ResolveOccurrencesUseCase,
) {
    fun execute(
        userId: UserId,
        range: ClosedRange<LocalDate>,
    ): Flow<AppResult<PlanAgenda>> =
        combine(
            taskRepository.observeAll(userId),
            activityRepository.observeAll(userId),
            habitRepository.observeAll(userId),
            recurrenceRuleRepository.observeAll(userId),
            sessionRepository.observeAll(userId),
        ) { tasks, activities, habits, rules, sessions ->
            buildPlan(userId, range, tasks, activities, habits, rules, sessions)
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load Plan range", cause)))
        }

    private suspend fun buildPlan(
        userId: UserId,
        range: ClosedRange<LocalDate>,
        tasks: List<Task>,
        activities: List<Activity>,
        habits: List<Habit>,
        rules: List<RecurrenceRule>,
        sessions: List<Session>,
    ): AppResult<PlanAgenda> {
        val rulesByOwner = rules.filterNot { it.sync.isDeleted }.groupBy { it.ownerType to it.ownerId }
        val sessionsById = sessions.filterNot { it.sync.isDeleted }.associateBy { it.id }
        val liveTasks = tasks.filterNot { it.sync.isDeleted || it.archived }
        val (tasksWithRule, tasksWithoutRule) =
            liveTasks.partition { (ExecutableKind.TASK to it.id.value) in rulesByOwner }
        val oneOffItems = buildOneOffTaskItems(range, tasksWithoutRule, sessionsById)

        val recurringTasksResult =
            resolveScheduledItems(
                userId,
                range,
                TodayItemKind.TASK,
                ExecutableKind.TASK,
                tasksWithRule.associateBy { it.id.value },
                rulesByOwner,
                sessionsById,
                resolveOccurrencesUseCase,
            ) { it.title to it.goalImpactScore }
        val activityItemsResult =
            resolveScheduledItems(
                userId,
                range,
                TodayItemKind.ACTIVITY,
                ExecutableKind.ACTIVITY,
                activities.filterNot { it.sync.isDeleted || it.archived }.associateBy { it.id.value },
                rulesByOwner,
                sessionsById,
                resolveOccurrencesUseCase,
            ) { it.title to null }
        val habitItemsResult =
            resolveScheduledItems(
                userId,
                range,
                TodayItemKind.HABIT,
                ExecutableKind.HABIT,
                habits.filterNot { it.sync.isDeleted || it.archived }.associateBy { it.id.value },
                rulesByOwner,
                sessionsById,
                resolveOccurrencesUseCase,
            ) { it.title to null }

        return recurringTasksResult.andThen { recurringTaskItems ->
            activityItemsResult.andThen { activityItems ->
                habitItemsResult.map { habitItems ->
                    assemblePlan(range, oneOffItems + recurringTaskItems + activityItems + habitItems)
                }
            }
        }
    }

    private fun buildOneOffTaskItems(
        range: ClosedRange<LocalDate>,
        tasksWithoutRule: List<Task>,
        sessionsById: Map<SessionId, Session>,
    ): List<TodayItem> =
        tasksWithoutRule.mapNotNull { task ->
            val date = task.date
            if (date == null || date !in range) return@mapNotNull null
            val session = sessionsById[SessionIdentity.deterministicId(ExecutableKind.TASK, task.id.value, date)]
            TodayItem(
                kind = TodayItemKind.TASK,
                sourceType = ExecutableKind.TASK,
                sourceId = task.id.value,
                title = task.title,
                date = date,
                time = task.time,
                isCompleted = session != null,
                goalImpactScore = task.goalImpactScore,
                recurrenceRuleId = null,
                occurrenceDate = null,
            )
        }

    private fun assemblePlan(
        range: ClosedRange<LocalDate>,
        items: List<TodayItem>,
    ): PlanAgenda {
        val itemsByDate = items.groupBy { it.date }
        val startEpoch = range.start.toEpochDays()
        val endEpoch = range.endInclusive.toEpochDays()
        val days =
            (startEpoch..endEpoch).map { epoch ->
                val date = LocalDate.fromEpochDays(epoch)
                PlanDay(date = date, items = (itemsByDate[date] ?: emptyList()).sortedWith(PLAN_ITEM_ORDER))
            }
        return PlanAgenda(range = range, days = days)
    }

    companion object {
        // Ordered by time of day (untimed items last), then title - a calendar day's own
        // natural order, deliberately not GetTodayAgendaUseCase's goalImpactScore-first ranking:
        // Plan shows a day's full shape as scheduled, not a "what matters most" priority list.
        private val PLAN_ITEM_ORDER =
            compareBy<TodayItem> { it.time ?: LocalTime(hour = 23, minute = 59) }
                .thenBy { it.title }
    }
}
