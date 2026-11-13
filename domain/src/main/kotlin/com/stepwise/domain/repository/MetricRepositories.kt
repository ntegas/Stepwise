package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.ProgressSourceKind
import kotlinx.coroutines.flow.Flow

// No userId-scoped reads — Metric is a global, shared reference catalog (its own KDoc in
// :core:model), not per-user data.
interface MetricRepository {
    fun observeAll(): Flow<List<Metric>>

    suspend fun getById(id: MetricId): AppResult<Metric?>

    suspend fun upsert(metric: Metric): AppResult<Unit>
}

interface GoalActivityLinkRepository {
    fun observeForSource(
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): Flow<List<GoalActivityLink>>

    fun observeForGoal(goalId: GoalId): Flow<List<GoalActivityLink>>

    suspend fun upsert(link: GoalActivityLink): AppResult<Unit>
}
