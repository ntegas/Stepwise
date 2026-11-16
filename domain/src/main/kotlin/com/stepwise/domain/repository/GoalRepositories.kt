package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeAll(userId: UserId): Flow<List<Goal>>

    fun observeById(id: GoalId): Flow<Goal?>

    suspend fun getById(id: GoalId): AppResult<Goal?>

    suspend fun upsert(goal: Goal): AppResult<Unit>
}

interface MilestoneRepository {
    fun observeForGoal(goalId: GoalId): Flow<List<Milestone>>

    // Added at DEV-011 - EditMilestoneUseCase needs a one-shot load by id to verify ownership
    // (via its parent Goal) and merge edits onto the existing row, the same reason DEV-009/010
    // added getById methods to other repositories exactly when a Use Case needed them.
    suspend fun getById(id: MilestoneId): AppResult<Milestone?>

    suspend fun upsert(milestone: Milestone): AppResult<Unit>
}

interface ProjectRepository {
    fun observeAll(userId: UserId): Flow<List<Project>>

    // Added at DEV-011 - CreateGoalUseCase/EditGoalUseCase need a one-shot load by id to verify
    // a Goal's projectId actually belongs to the caller, the same reason MilestoneRepository
    // grew its own getById.
    suspend fun getById(id: ProjectId): AppResult<Project?>

    suspend fun upsert(project: Project): AppResult<Unit>
}
