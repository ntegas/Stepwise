package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRecurrenceOccurrenceRepository : RecurrenceOccurrenceRepository {
    private val occurrences = MutableStateFlow<Map<String, RecurrenceOccurrence>>(emptyMap())

    fun seed(occurrence: RecurrenceOccurrence) {
        occurrences.value = occurrences.value + (occurrence.id.value to occurrence)
    }

    fun get(id: RecurrenceOccurrenceId): RecurrenceOccurrence? = occurrences.value[id.value]

    override fun observeForRule(ruleId: RecurrenceRuleId): Flow<List<RecurrenceOccurrence>> =
        occurrences.map { all -> all.values.filter { it.recurrenceRuleId == ruleId } }

    override suspend fun getById(id: RecurrenceOccurrenceId): AppResult<RecurrenceOccurrence?> =
        AppResult.Success(occurrences.value[id.value])

    override suspend fun upsert(occurrence: RecurrenceOccurrence): AppResult<Unit> {
        occurrences.value = occurrences.value + (occurrence.id.value to occurrence)
        return AppResult.Success(Unit)
    }
}
