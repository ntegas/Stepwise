package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.Project
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeAll(userId: UserId): Flow<List<Goal>>

    suspend fun getById(id: GoalId): AppResult<Goal?>

    suspend fun upsert(goal: Goal): AppResult<Unit>
}

interface MilestoneRepository {
    fun observeForGoal(goalId: GoalId): Flow<List<Milestone>>

    suspend fun upsert(milestone: Milestone): AppResult<Unit>
}

interface ProjectRepository {
    fun observeAll(userId: UserId): Flow<List<Project>>

    suspend fun upsert(project: Project): AppResult<Unit>
}
