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
import kotlinx.coroutines.flow.map

/**
 * The Goals UI's (DEV-016) own read-side Use Cases — each a thin pass-through over its
 * repository's existing `observeAll`/`observeForGoal`. `/ARCHITECTURE.md`'s own layering diagram
 * (`Compose UI -> ViewModel -> Use Case -> Repository`) states a ViewModel calls Use Cases, never
 * a Repository directly; these exist so `GoalsListViewModel`/`GoalFormViewModel`/
 * `GoalDetailViewModel` can follow that rule for a plain reactive read the same way every write
 * path already does, rather than reaching past it for lack of anything to aggregate. Each wraps
 * its Flow in [AppResult] and adds a `.catch` - [GetTodayAgendaUseCase]'s own review already
 * established that a repository Flow's platform exception must never escape a Use Case boundary
 * uncaught; the same rule applies here even though none of these aggregate multiple sources.
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
    suspend fun execute(goalId: GoalId): AppResult<Goal?> = goalRepository.getById(goalId)
}

class GetMilestonesUseCase(
    private val milestoneRepository: MilestoneRepository,
) {
    fun execute(goalId: GoalId): Flow<AppResult<List<Milestone>>> =
        milestoneRepository
            .observeForGoal(goalId)
            .map<List<Milestone>, AppResult<List<Milestone>>> { AppResult.Success(it) }
            .catch { cause ->
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
