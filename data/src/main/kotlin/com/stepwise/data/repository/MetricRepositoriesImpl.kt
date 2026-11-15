package com.stepwise.data.repository

import androidx.room.withTransaction
import com.stepwise.core.common.AppResult
import com.stepwise.core.database.GoalActivityLinkDao
import com.stepwise.core.database.MetricDao
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
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

/**
 * Takes the whole [StepwiseDatabase], not just [GoalActivityLinkDao], the same reason
 * `RecurrenceOccurrenceRepositoryImpl` does (DEV-010) — [insertIfNotDuplicate] needs
 * [androidx.room.withTransaction] to close a real TOCTOU race the DEV-012 adversarial review
 * found: a plain check-then-write (no transaction) let two concurrent [LinkActivityToGoalUseCase]
 * calls both pass the duplicate check before either commit, since each mints its own fresh
 * [GoalActivityLinkId] and the two rows don't collide on primary key. Fixed two ways, the same
 * "domain-layer transaction + DB-level backstop" pattern `sessions`/`SessionEntity`'s own unique
 * `clientEventId` index already established: the check-then-write now happens inside one
 * [androidx.room.withTransaction] block (closing it directly), and [GoalActivityLinkEntity]'s own
 * `indices` gained a unique index on `(sourceType, sourceId, goalId, metricId)` mirroring
 * Postgres's own `goal_activity_links_unique` constraint (so even a future write path that skips
 * this method entirely still can't persist a duplicate).
 */
class GoalActivityLinkRepositoryImpl(
    private val database: StepwiseDatabase,
) : GoalActivityLinkRepository {
    private val dao: GoalActivityLinkDao get() = database.goalActivityLinkDao()

    override fun observeForSource(
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): Flow<List<GoalActivityLink>> =
        dao.observeForSource(sourceType, sourceId).map { entities -> entities.map { it.toDomain() } }

    override fun observeForGoal(goalId: GoalId): Flow<List<GoalActivityLink>> =
        dao.observeForGoal(goalId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: GoalActivityLinkId): AppResult<GoalActivityLink?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun insertIfNotDuplicate(link: GoalActivityLink): AppResult<GoalActivityLink?> =
        runCatchingDatabaseCall {
            database.withTransaction {
                val existing =
                    dao.getForSourceGoalMetric(link.sourceType, link.sourceId, link.goalId.value, link.metricId.value)
                if (existing != null) {
                    null
                } else {
                    dao.upsert(link.toEntity())
                    link
                }
            }
        }

    override suspend fun upsert(link: GoalActivityLink): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(link.toEntity()) }
}
