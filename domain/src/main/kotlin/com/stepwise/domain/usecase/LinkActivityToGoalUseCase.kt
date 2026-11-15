package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.model.ActivityId
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.HabitId
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.SyncMetadata
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.ActivityRepository
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.HabitRepository
import com.stepwise.domain.repository.MetricRepository

/**
 * Creates a [GoalActivityLink] (`/ROADMAP.md` DEV-012: "one Activity/Habit can update multiple
 * Goals, each via a different metric"). Verifies the caller owns **both** the source (an
 * [com.stepwise.core.model.Activity] or [com.stepwise.core.model.Habit], per [ProgressSourceKind])
 * and the target [com.stepwise.core.model.Goal] before creating the link — a Postgres FK alone
 * would only confirm both rows exist, not that they belong to the same caller
 * (`/docs/security/THREAT_MODEL.md`'s sync section already closes this exact shape of gap for
 * `goal_activity_links.source_id` with a dedicated ownership-consistency trigger; this is the
 * client-side defense-in-depth counterpart, same reasoning [CrossEntityOwnership] states).
 *
 * **No canonical doc states a no-duplicate-link rule explicitly, but one is enforced anyway**:
 * two links with identical `(sourceType, sourceId, goalId, metricId)` would silently double-count
 * that source's contribution once the Calculation Engine (DEV-019, `:core:calculation` still
 * doesn't exist) sums `GoalActivityLink` rows for a Goal — the same category of narrow, defensible
 * default as DEV-011's `targetValue`/`weight` sanity checks, not an invented product rule. The
 * check-then-write is atomic
 * ([insertIfNotDuplicate][com.stepwise.domain.repository.GoalActivityLinkRepository.insertIfNotDuplicate]),
 * not a separate check followed by a separate write — DEV-012 adversarial review, HIGH: two
 * concurrent calls could otherwise both pass a plain check before either commit, since each
 * mints its own fresh id and the rows don't collide on primary key.
 *
 * **Disclosed, not constrained**: for a `HABIT` source, [metricId][LinkActivityToGoalCommand.metricId]
 * is not required to equal that Habit's own `metricId`. A default (non-overridden) Habit
 * completion always resolves against `Habit.metricId` ([MetricResolver.habitDefaultMetrics]), so a
 * link created against a different metric would never receive a contribution through that
 * default path — silently inert, not double-counting. No canonical doc states either way whether
 * this should be constrained; left open rather than guessed past, for DEV-019 (Calculation Engine)
 * or the product owner to decide once it matters.
 */
class LinkActivityToGoalUseCase(
    private val activityRepository: ActivityRepository,
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
    private val metricRepository: MetricRepository,
    private val goalActivityLinkRepository: GoalActivityLinkRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend fun execute(command: LinkActivityToGoalCommand): AppResult<GoalActivityLink> =
        verifyLinkIsValid(command).andThen {
            val link = buildLink(command)
            goalActivityLinkRepository.insertIfNotDuplicate(link).andThen { inserted ->
                if (inserted == null) {
                    AppResult.Failure(
                        AppError.Validation(
                            "This source is already linked to this Goal via this metric",
                            field = "metricId",
                        ),
                    )
                } else {
                    AppResult.Success(inserted)
                }
            }
        }

    private suspend fun verifyLinkIsValid(command: LinkActivityToGoalCommand): AppResult<Unit> =
        verifySource(command.userId, command.sourceType, command.sourceId).andThen {
            GoalAccess.verifyOwnedGoal(goalRepository, command.userId, command.goalId).andThen {
                verifyMetricExists(command.metricId)
            }
        }

    private suspend fun verifySource(
        userId: UserId,
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): AppResult<Unit> =
        when (sourceType) {
            ProgressSourceKind.ACTIVITY ->
                ActivityAccess.verifyOwnedActivity(activityRepository, userId, ActivityId(sourceId)).map { }
            ProgressSourceKind.HABIT ->
                HabitAccess.verifyOwnedHabit(habitRepository, userId, HabitId(sourceId)).map { }
        }

    private suspend fun verifyMetricExists(metricId: MetricId): AppResult<Unit> =
        metricRepository.getById(metricId).andThen { metric ->
            if (metric == null) {
                AppResult.Failure(AppError.Validation("Metric not found", field = "metricId"))
            } else {
                AppResult.Success(Unit)
            }
        }

    private fun buildLink(command: LinkActivityToGoalCommand): GoalActivityLink =
        GoalActivityLink(
            id = GoalActivityLinkId(idGenerator.newId()),
            sourceType = command.sourceType,
            sourceId = command.sourceId,
            goalId = command.goalId,
            metricId = command.metricId,
            sync = SyncMetadata(updatedAt = clock.now(), version = 1, deletedAt = null),
        )
}
