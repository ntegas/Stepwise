package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.Project
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.LifeAreaRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The Goals UI's (DEV-016) own read-side Use Cases — each a thin pass-through over its
 * repository's existing `observeAll`/`observeForGoal`/`observeById`. `/ARCHITECTURE.md`'s own
 * layering diagram (`Compose UI -> ViewModel -> Use Case -> Repository`) states a ViewModel calls
 * Use Cases, never a Repository directly; these exist so `GoalsListViewModel`/`GoalFormViewModel`/
 * `GoalDetailViewModel` can follow that rule for a plain reactive read the same way every write
 * path already does, rather than reaching past it for lack of anything to aggregate. Each wraps
 * its Flow in [AppResult] and adds a `.catch` - [GetTodayAgendaUseCase]'s own review already
 * established that a repository Flow's platform exception must never escape a Use Case boundary
 * uncaught; the same rule applies here even though none of these aggregate multiple sources.
 *
 * [GetGoalByIdUseCase], [ObserveGoalUseCase], and [GetMilestonesUseCase] all route through
 * [GoalAccess.verifyOwnedGoal] rather than reading by a bare id — DEV-016 logic review (HIGH):
 * every *write*-side Use Case already centralizes its ownership check there, and these read-side
 * ones diverged from that pattern in the first draft. A Goal that doesn't exist and a Goal that
 * exists but belongs to someone else are deliberately indistinguishable to the caller (both read
 * back as "not found"), the same 404-shaped non-disclosure [GoalAccess] already gives write paths.
 */
class GetGoalsUseCase(
    private val goalRepository: GoalRepository,
) {
    fun execute(userId: UserId): Flow<AppResult<List<Goal>>> =
        goalRepository
            .observeAll(userId)
            .map<List<Goal>, AppResult<List<Goal>>> { AppResult.Success(it) }
            .catch { cause ->
                emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load goals", cause)))
            }
}

class GetGoalByIdUseCase(
    private val goalRepository: GoalRepository,
) {
    suspend fun execute(
        userId: UserId,
        goalId: GoalId,
    ): AppResult<Goal> = GoalAccess.verifyOwnedGoal(goalRepository, userId, goalId)
}

/**
 * [GoalDetailViewModel][com.stepwise.feature.goals.GoalDetailViewModel]'s own reactive single-Goal
 * read — added at DEV-016 architecture review (MEDIUM) after that ViewModel's first draft observed
 * [GetGoalsUseCase]'s entire per-user list and filtered out the one Goal it needed, the exact
 * "one broad query multiple screens over-recompose from" anti-pattern `/ARCHITECTURE.md` §22 names.
 * [GoalRepository.observeById] is a narrow, PK-scoped Room query, so this screen's cost no longer
 * scales with the user's total Goal count. Ownership is verified once, up front, before switching
 * to the reactive stream - a Goal's `userId` is immutable once created, so there is nothing to
 * re-verify on each later emission. A soft-delete happening *after* that check is still caught on
 * every later emission, though: `GoalDao.observeById` has no `deletedAt` filter of its own (the
 * same convention `GoalDao.getById`/[GoalAccess.verifyOwnedGoal] already use - fetch
 * unconditionally, let the caller decide), so this execute() re-checks `sync.isDeleted` per
 * emission rather than only at the one-time ownership check.
 */
class ObserveGoalUseCase(
    private val goalRepository: GoalRepository,
) {
    fun execute(
        userId: UserId,
        goalId: GoalId,
    ): Flow<AppResult<Goal?>> =
        flow {
            val ownership = GoalAccess.verifyOwnedGoal(goalRepository, userId, goalId)
            if (ownership is AppResult.Failure) {
                // Not found and not owned read back identically here - see this file's own KDoc.
                emit(AppResult.Success<Goal?>(null))
                return@flow
            }
            emitAll(
                goalRepository.observeById(goalId).map<Goal?, AppResult<Goal?>> { goal ->
                    AppResult.Success(goal?.takeIf { !it.sync.isDeleted })
                },
            )
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load goal", cause)))
        }
}

class GetMilestonesUseCase(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository,
) {
    fun execute(
        userId: UserId,
        goalId: GoalId,
    ): Flow<AppResult<List<Milestone>>> =
        flow {
            val ownership = GoalAccess.verifyOwnedGoal(goalRepository, userId, goalId)
            if (ownership is AppResult.Failure) {
                emit(AppResult.Failure(ownership.error))
                return@flow
            }
            emitAll(
                milestoneRepository
                    .observeForGoal(goalId)
                    .map<List<Milestone>, AppResult<List<Milestone>>> { AppResult.Success(it) },
            )
        }.catch { cause ->
            emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load milestones", cause)))
        }
}

class GetLifeAreasUseCase(
    private val lifeAreaRepository: LifeAreaRepository,
) {
    fun execute(userId: UserId): Flow<AppResult<List<LifeArea>>> =
        lifeAreaRepository
            .observeAll(userId)
            .map<List<LifeArea>, AppResult<List<LifeArea>>> { AppResult.Success(it) }
            .catch { cause ->
                emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load life areas", cause)))
            }
}

class GetProjectsUseCase(
    private val projectRepository: ProjectRepository,
) {
    fun execute(userId: UserId): Flow<AppResult<List<Project>>> =
        projectRepository
            .observeAll(userId)
            .map<List<Project>, AppResult<List<Project>>> { AppResult.Success(it) }
            .catch { cause ->
                emit(AppResult.Failure(AppError.Unexpected(cause.message ?: "Failed to load projects", cause)))
            }
}
