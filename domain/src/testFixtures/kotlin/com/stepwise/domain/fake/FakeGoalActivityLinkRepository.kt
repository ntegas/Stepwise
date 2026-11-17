package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.GoalActivityLink
import com.stepwise.core.model.GoalActivityLinkId
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.domain.repository.GoalActivityLinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGoalActivityLinkRepository : GoalActivityLinkRepository {
    private val links = MutableStateFlow<Map<String, GoalActivityLink>>(emptyMap())

    fun seed(link: GoalActivityLink) {
        links.value = links.value + (link.id.value to link)
    }

    fun get(id: GoalActivityLinkId): GoalActivityLink? = links.value[id.value]

    override fun observeForSource(
        sourceType: ProgressSourceKind,
        sourceId: String,
    ): Flow<List<GoalActivityLink>> =
        links.map { all ->
            all.values.filter {
                it.sourceType == sourceType && it.sourceId == sourceId && !it.sync.isDeleted
            }
        }

    override fun observeForGoal(goalId: GoalId): Flow<List<GoalActivityLink>> =
        links.map { all -> all.values.filter { it.goalId == goalId && !it.sync.isDeleted } }

    override suspend fun getById(id: GoalActivityLinkId): AppResult<GoalActivityLink?> =
        AppResult.Success(links.value[id.value])

    // Single-threaded test fake - no real concurrency to race, but mirrors the real
    // check-then-write-as-one-step shape so a test exercising this path matches production.
    override suspend fun insertIfNotDuplicate(link: GoalActivityLink): AppResult<GoalActivityLink?> {
        val existing =
            links.value.values.firstOrNull {
                it.sourceType == link.sourceType &&
                    it.sourceId == link.sourceId &&
                    it.goalId == link.goalId &&
                    it.metricId == link.metricId &&
                    !it.sync.isDeleted
            }
        if (existing != null) {
            return AppResult.Success(null)
        }
        links.value = links.value + (link.id.value to link)
        return AppResult.Success(link)
    }

    override suspend fun upsert(link: GoalActivityLink): AppResult<Unit> {
        links.value = links.value + (link.id.value to link)
        return AppResult.Success(Unit)
    }
}
