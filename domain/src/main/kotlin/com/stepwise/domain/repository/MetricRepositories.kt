package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
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

    // Added at DEV-012 - UnlinkActivityFromGoalUseCase needs a one-shot load by id to verify
    // ownership (via its parent Goal) before tombstoning it.
    suspend fun getById(id: GoalActivityLinkId): AppResult<GoalActivityLink?>

    // Added at DEV-012 - LinkActivityToGoalUseCase rejects an exact-duplicate link before
    // creating one (see its own KDoc for why — the Calculation Engine would otherwise
    // double-count the same source's contribution to the same Goal via the same metric). Atomic
    // (one transaction) rather than a separate check + upsert, closing a real TOCTOU race the
    // DEV-012 adversarial review found - see GoalActivityLinkRepositoryImpl's own KDoc for the
    // full exploit. Returns null (no insert happened) when the link already exists.
    suspend fun insertIfNotDuplicate(link: GoalActivityLink): AppResult<GoalActivityLink?>

    suspend fun upsert(link: GoalActivityLink): AppResult<Unit>
}
