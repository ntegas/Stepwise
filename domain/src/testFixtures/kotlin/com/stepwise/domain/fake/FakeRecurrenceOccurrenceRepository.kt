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

    override suspend fun getAllForRule(ruleId: RecurrenceRuleId): AppResult<List<RecurrenceOccurrence>> =
        AppResult.Success(occurrences.value.values.filter { it.recurrenceRuleId == ruleId })

    override suspend fun upsert(occurrence: RecurrenceOccurrence): AppResult<Unit> {
        occurrences.value = occurrences.value + (occurrence.id.value to occurrence)
        return AppResult.Success(Unit)
    }

    // A real StateFlow update is as close to "one atomic step" as this in-memory fake can get -
    // no separate connection/writer to race against, unlike the real Room-backed impl this
    // mirrors (DEV-010 review). Good enough to exercise the use cases' own logic; the actual
    // race-closing behavior is only genuinely verified by CI against the real implementation.
    override suspend fun upsertAtomically(
        id: RecurrenceOccurrenceId,
        mutate: (RecurrenceOccurrence?) -> AppResult<RecurrenceOccurrence>,
    ): AppResult<RecurrenceOccurrence> =
        when (val outcome = mutate(occurrences.value[id.value])) {
            is AppResult.Success -> {
                occurrences.value = occurrences.value + (outcome.value.id.value to outcome.value)
                outcome
            }
            is AppResult.Failure -> outcome
        }
}
