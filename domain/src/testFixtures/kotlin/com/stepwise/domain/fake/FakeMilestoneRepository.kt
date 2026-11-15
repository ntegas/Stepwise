package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.GoalId
import com.stepwise.core.model.Milestone
import com.stepwise.core.model.MilestoneId
import com.stepwise.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMilestoneRepository : MilestoneRepository {
    private val milestones = MutableStateFlow<Map<String, Milestone>>(emptyMap())

    fun seed(milestone: Milestone) {
        milestones.value = milestones.value + (milestone.id.value to milestone)
    }

    fun get(id: MilestoneId): Milestone? = milestones.value[id.value]

    override fun observeForGoal(goalId: GoalId): Flow<List<Milestone>> =
        milestones.map { all -> all.values.filter { it.goalId == goalId } }

    override suspend fun getById(id: MilestoneId): AppResult<Milestone?> = AppResult.Success(milestones.value[id.value])

    override suspend fun upsert(milestone: Milestone): AppResult<Unit> {
        milestones.value = milestones.value + (milestone.id.value to milestone)
        return AppResult.Success(Unit)
    }
}
