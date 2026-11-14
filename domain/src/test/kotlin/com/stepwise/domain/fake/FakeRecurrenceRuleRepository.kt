package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.RecurrenceRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRecurrenceRuleRepository : RecurrenceRuleRepository {
    private val rules = MutableStateFlow<Map<String, RecurrenceRule>>(emptyMap())

    fun seed(rule: RecurrenceRule) {
        rules.value = rules.value + (rule.id.value to rule)
    }

    override fun observeAll(userId: UserId): Flow<List<RecurrenceRule>> =
        rules.map { all -> all.values.filter { it.userId == userId } }

    override fun observeForOwner(
        ownerType: ExecutableKind,
        ownerId: String,
    ): Flow<List<RecurrenceRule>> =
        rules.map { all -> all.values.filter { it.ownerType == ownerType && it.ownerId == ownerId } }

    override suspend fun getById(id: RecurrenceRuleId): AppResult<RecurrenceRule?> =
        AppResult.Success(rules.value[id.value])

    override suspend fun upsert(rule: RecurrenceRule): AppResult<Unit> {
        rules.value = rules.value + (rule.id.value to rule)
        return AppResult.Success(Unit)
    }
}
