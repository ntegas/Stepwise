package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.GoalDao
import com.stepwise.core.database.MilestoneDao
import com.stepwise.core.database.ProjectDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.Goal
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.core.model.Project
import com.stepwise.core.model.ProjectId
import com.stepwise.core.model.UserId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.GoalRepository
import com.stepwise.domain.repository.MilestoneRepository
import com.stepwise.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepositoryImpl(
    private val dao: GoalDao,
) : GoalRepository {
    override fun observeAll(userId: UserId): Flow<List<Goal>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: GoalId): AppResult<Goal?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(goal: Goal): AppResult<Unit> = runCatchingDatabaseCall { dao.upsert(goal.toEntity()) }
}

class MilestoneRepositoryImpl(
    private val dao: MilestoneDao,
) : MilestoneRepository {
    override fun observeForGoal(goalId: GoalId): Flow<List<Milestone>> =
        dao.observeForGoal(goalId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: MilestoneId): AppResult<Milestone?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(milestone: Milestone): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(milestone.toEntity()) }
}

class ProjectRepositoryImpl(
    private val dao: ProjectDao,
) : ProjectRepository {
    override fun observeAll(userId: UserId): Flow<List<Project>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: ProjectId): AppResult<Project?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(project: Project): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(project.toEntity()) }
}
