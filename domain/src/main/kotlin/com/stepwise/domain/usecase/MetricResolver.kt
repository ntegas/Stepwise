package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.TaskId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.TaskRepository

/**
 * Resolves the final [MetricEntry] list a [CompleteOccurrenceUseCase] writes against a Session —
 * split out of that class (DEV-009 self-review, same lesson DEV-008's `SessionDao`/`SessionSyncDao`
 * split already taught: detekt's `TooManyFunctions` is a real signal a class is doing two
 * genuinely separable jobs, not a threshold worth raising). This one job is: load each source
 * entity's own default metric (if it has one), merge in the caller's overrides, then apply
 * "Done=Planned default; Partial=Actual" (`/docs/dev-plan-detailed.md`'s DEV-009 checklist) per
 * entry — ownership verification happens here too, since loading the entity to read its default
 * metric is also the one place that has the entity in hand to check against.
 */
class MetricResolver(
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
) {
    suspend fun resolve(
        userId: UserId,
        target: CompletionTarget,
        status: ExecutionStatus,
        overrides: List<MetricEntry>,
    ): AppResult<List<MetricEntry>> =
        loadDefaultMetrics(userId, target).andThen { defaults -> resolveMerged(status, defaults, overrides) }

    private fun resolveMerged(
        status: ExecutionStatus,
        defaults: List<MetricEntry>,
        overrides: List<MetricEntry>,
    ): AppResult<List<MetricEntry>> {
        if (status != ExecutionStatus.DONE && status != ExecutionStatus.PARTIAL) {
            return AppResult.Success(emptyList())
        }
        val merged = mergeMetricEntries(defaults, overrides)
        val resolved = merged.map { entry -> resolveOneMetric(status, entry) }
        val firstFailure = resolved.filterIsInstance<AppResult.Failure>().firstOrNull()
        return firstFailure ?: AppResult.Success(resolved.map { (it as AppResult.Success).value })
    }

    private suspend fun loadDefaultMetrics(
        userId: UserId,
        target: CompletionTarget,
    ): AppResult<List<MetricEntry>> =
        when (target.sourceType) {
            ExecutableKind.TASK -> taskDefaultMetrics(userId, target.sourceId)
            ExecutableKind.ACTIVITY -> activityDefaultMetrics(userId, target.sourceId)
            ExecutableKind.HABIT -> habitDefaultMetrics(userId, target.sourceId)
        }

    private suspend fun taskDefaultMetrics(
        userId: UserId,
        taskId: String,
    ): AppResult<List<MetricEntry>> =
        taskRepository.getById(TaskId(taskId)).andThen { task ->
            // task?.metricId captured as a local val, not re-read off `task` in the `else` branch -
            // a property declared in a different module (:core:model's Task) can't be smart-cast
            // from nullable to non-null based on a when-branch check the way a local val can
            // (a real compile error found by CI, not a style nit).
            val metricId = task?.metricId
            when {
                task == null -> AppResult.Failure(AppError.Validation("Task not found", field = "sourceId"))
                task.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Task"))
                metricId == null -> AppResult.Success(emptyList())
                else -> AppResult.Success(listOf(MetricEntry(metricId, task.plannedResult, actualValue = null)))
            }
        }

    private suspend fun activityDefaultMetrics(
        userId: UserId,
        activityId: String,
    ): AppResult<List<MetricEntry>> =
        activityRepository.getById(ActivityId(activityId)).andThen { activity ->
            when {
                activity == null -> AppResult.Failure(AppError.Validation("Activity not found", field = "sourceId"))
                activity.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Activity"))
                // An Activity has no entity-level metric of its own (concept §19-20) - the
                // caller's own overrides fully determine what this session logs.
                else -> AppResult.Success(emptyList())
            }
        }

    private suspend fun habitDefaultMetrics(
        userId: UserId,
        habitId: String,
    ): AppResult<List<MetricEntry>> =
        habitRepository.getById(HabitId(habitId)).andThen { habit ->
            when {
                habit == null -> AppResult.Failure(AppError.Validation("Habit not found", field = "sourceId"))
                habit.userId != userId -> AppResult.Failure(AppError.Auth("Not authorized for this Habit"))
                else -> AppResult.Success(listOf(MetricEntry(habit.metricId, habit.targetValue, actualValue = null)))
            }
        }

    private fun mergeMetricEntries(
        defaults: List<MetricEntry>,
        overrides: List<MetricEntry>,
    ): List<MetricEntry> {
        val overrideMetricIds = overrides.map { it.metricId }.toSet()
        return defaults.filter { it.metricId !in overrideMetricIds } + overrides
    }

    private fun resolveOneMetric(
        status: ExecutionStatus,
        entry: MetricEntry,
    ): AppResult<MetricEntry> {
        // "Done=Planned default; Partial=Actual" - DONE may fall back to the planned value,
        // PARTIAL never does (defaulting a deliberately-incomplete result to the full plan would
        // silently misrecord it).
        val resolvedActual =
            when (status) {
                ExecutionStatus.DONE -> entry.actualValue ?: entry.plannedValue
                else -> entry.actualValue
            }
        return if (resolvedActual == null) {
            AppResult.Failure(
                AppError.Validation(
                    "$status requires an actual value for metric ${entry.metricId.value}" +
                        " (a planned-value default only applies to DONE)",
                    field = "actualValue",
                ),
            )
        } else {
            AppResult.Success(entry.copy(actualValue = resolvedActual))
        }
    }
}
