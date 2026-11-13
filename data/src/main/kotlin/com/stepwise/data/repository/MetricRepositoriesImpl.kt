package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.GoalActivityLinkDao
import com.stepwise.core.database.MetricDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.GoalActivityLinkRepository
import com.stepwise.domain.repository.MetricRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MetricRepositoryImpl(
    private val dao: MetricDao,
) : MetricRepository {
    override fun observeAll(): Flow<List<Metric>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: MetricId): AppResult<Metric?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(metric: Metric): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(metric.toEntity()) }
}

class GoalActivityLinkRepositoryImpl(
    private val dao: GoalActivityLinkDao,
) : GoalActivityLinkRepository {
    override fun observeForSource(
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): Flow<List<GoalActivityLink>> =
        dao.observeForSource(sourceType, sourceId).map { entities -> entities.map { it.toDomain() } }

    override fun observeForGoal(goalId: GoalId): Flow<List<GoalActivityLink>> =
        dao.observeForGoal(goalId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsert(link: GoalActivityLink): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(link.toEntity()) }
}
