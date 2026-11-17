package com.stepwise.domain.usecase

import com.stepwise.core.calculation.GoalProgressResult
import com.stepwise.core.calculation.MilestoneProgressInput
import com.stepwise.core.calculation.PeriodRange
import com.stepwise.core.calculation.frequencyProgress
import com.stepwise.core.calculation.metricProgress
import com.stepwise.core.calculation.milestonesProgress
import com.stepwise.core.calculation.targetValueProgress
import com.stepwise.core.calculation.tasksProgress
import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.SessionRepository
import com.stepwise.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first

/**
 * Computes one Goal's progress per its own `progressMode` (`CALCULATION_ENGINE.md`'s "Goal
 * Progress, per GoalProgressMode") — the `:domain`-side half of that contract: this class does
 * every repository join, `:core:calculation`'s pure functions do every actual formula. A plain
 * one-shot `suspend fun`, not a reactive `Flow`, since nothing UI-facing consumes this yet
 * (CALCULATION_ENGINE.md's own stated scope is the calculation layer, not a new screen) — the
 * same "don't build speculative infrastructure ahead of a concrete consumer" reasoning this
 * project already applies elsewhere; a future screen that needs live updates wraps this in its
 * own reactive re-query, the same way `GetTodayAgendaUseCase` itself re-queries on every
 * `combine` tick rather than this layer trying to guess at staleness.
 *
 * `GoalProgressMode.MANUAL` has no backing field anywhere in the canonical `Goal` entity to read
 * a stored value from (`CALCULATION_ENGINE.md`'s own "MANUAL mode" section) — returns
 * [AppError.Domain] rather than guessing a number or crashing.
 */
class GetGoalProgressUseCase(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository,
    private val goalActivityLinkRepository: GoalActivityLinkRepository,
    private val sessionRepository: SessionRepository,
    private val taskRepository: TaskRepository,
) {
    suspend fun execute(
        userId: UserId,
        goalId: GoalId,
        period: PeriodRange,
    ): AppResult<GoalProgressResult> =
        GoalAccess.verifyOwnedGoal(goalRepository, userId, goalId).andThen { goal ->
            when (goal.progressMode) {
                GoalProgressMode.METRIC -> metricResult(goal)
                GoalProgressMode.TARGET_VALUE -> targetValueResult(goal)
                GoalProgressMode.FREQUENCY -> frequencyResult(goal, period)
                GoalProgressMode.MILESTONES -> milestonesResult(goal)
                GoalProgressMode.TASKS -> tasksResult(userId, goal)
                GoalProgressMode.MANUAL ->
                    AppResult.Failure(
                        AppError.Domain("MANUAL progress has no stored value yet - see CALCULATION_ENGINE.md"),
                    )
            }
        }

    private suspend fun metricResult(goal: Goal): AppResult<GoalProgressResult> {
        val target = goal.targetValue ?: return invalidTarget()
        return actualValuesForGoal(goal.id).map { metricProgress(target, it) }
    }

    private suspend fun targetValueResult(goal: Goal): AppResult<GoalProgressResult> {
        val target = goal.targetValue ?: return invalidTarget()
        return mostRecentActualValueForGoal(goal.id).map { targetValueProgress(target, it) }
    }

    private suspend fun frequencyResult(
        goal: Goal,
        period: PeriodRange,
    ): AppResult<GoalProgressResult> {
        val target = goal.targetValue ?: return invalidTarget()
        return qualifyingSessionCountForGoal(goal.id, period).map { frequencyProgress(target, it) }
    }

    private suspend fun milestonesResult(goal: Goal): AppResult<GoalProgressResult> {
        val milestones = milestoneRepository.observeForGoal(goal.id).first().filterNot { it.sync.isDeleted }
        val inputs =
            milestones.map {
                MilestoneProgressInput(
                    weight = it.weight,
                    isBinary = it.isBinary,
                    completed = it.completed,
                    progressPercent = it.progressPercent,
                )
            }
        return AppResult.Success(milestonesProgress(inputs))
    }

    private suspend fun tasksResult(
        userId: UserId,
        goal: Goal,
    ): AppResult<GoalProgressResult> {
        val tasks = taskRepository.observeAll(userId).first().filter { it.goalId == goal.id && !it.archived }
        var completedCount = 0
        for (task in tasks) {
            val sessions = sessionRepository.observeForSource(ExecutableKind.TASK, task.id.value).first()
            if (sessions.any { it.status == ExecutionStatus.DONE }) completedCount++
        }
        return AppResult.Success(tasksProgress(completedCount, tasks.size))
    }

    private suspend fun linksForGoal(goalId: GoalId): List<GoalActivityLink> =
        goalActivityLinkRepository.observeForGoal(goalId).first().filterNot { it.sync.isDeleted }

    private suspend fun sessionsForLink(link: GoalActivityLink): List<Session> =
        sessionRepository.observeForSource(link.sourceType.toExecutableKind(), link.sourceId).first()

    private suspend fun actualValuesForGoal(goalId: GoalId): AppResult<List<Double>> {
        val values = mutableListOf<Double>()
        for (link in linksForGoal(goalId)) {
            for (session in sessionsForLink(link)) {
                val metricValues = sessionRepository.getMetricValuesForSession(session.id)
                if (metricValues is AppResult.Failure) return metricValues
                if (metricValues is AppResult.Success) {
                    metricValues.value.filter { it.metricId == link.metricId }.forEach { values += it.actualValue }
                }
            }
        }
        return AppResult.Success(values)
    }

    private suspend fun mostRecentActualValueForGoal(goalId: GoalId): AppResult<Double?> {
        var mostRecent: Pair<Session, Double>? = null
        for (link in linksForGoal(goalId)) {
            for (session in sessionsForLink(link)) {
                val metricValues = sessionRepository.getMetricValuesForSession(session.id)
                if (metricValues is AppResult.Failure) return metricValues
                if (metricValues !is AppResult.Success) continue
                val matching = metricValues.value.filter { it.metricId == link.metricId }
                for (metricValue in matching) {
                    if (mostRecent == null || session.isMoreRecentThan(mostRecent.first)) {
                        mostRecent = session to metricValue.actualValue
                    }
                }
            }
        }
        return AppResult.Success(mostRecent?.second)
    }

    private suspend fun qualifyingSessionCountForGoal(
        goalId: GoalId,
        period: PeriodRange,
    ): AppResult<Int> {
        var count = 0
        for (link in linksForGoal(goalId)) {
            for (session in sessionsForLink(link)) {
                val qualifies =
                    session.date in period &&
                        (session.status == ExecutionStatus.DONE || session.status == ExecutionStatus.PARTIAL)
                if (qualifies) count++
            }
        }
        return AppResult.Success(count)
    }

    private fun invalidTarget(): AppResult<GoalProgressResult> =
        AppResult.Failure(AppError.Validation("This mode requires a targetValue", field = "targetValue"))
}

private fun Session.isMoreRecentThan(other: Session): Boolean =
    if (date != other.date) date > other.date else sync.updatedAt > other.sync.updatedAt

private fun ProgressSourceKind.toExecutableKind(): ExecutableKind =
    when (this) {
        ProgressSourceKind.ACTIVITY -> ExecutableKind.ACTIVITY
        ProgressSourceKind.HABIT -> ExecutableKind.HABIT
    }
