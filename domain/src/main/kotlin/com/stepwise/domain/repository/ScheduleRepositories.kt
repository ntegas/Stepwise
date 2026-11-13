package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface RecurrenceRuleRepository {
    fun observeAll(userId: UserId): Flow<List<RecurrenceRule>>

    fun observeForOwner(
        ownerType: ExecutableKind,
        ownerId: String,
    ): Flow<List<RecurrenceRule>>

    suspend fun upsert(rule: RecurrenceRule): AppResult<Unit>
}

// Only materialized occurrences get a row (`/ARCHITECTURE.md` §13) — the common case
// (an unmodified virtual occurrence) is computed on read by DEV-010's occurrence
// resolution, never stored or exposed through this repository.
interface RecurrenceOccurrenceRepository {
    fun observeForRule(ruleId: RecurrenceRuleId): Flow<List<RecurrenceOccurrence>>

    suspend fun getById(id: RecurrenceOccurrenceId): AppResult<RecurrenceOccurrence?>

    suspend fun upsert(occurrence: RecurrenceOccurrence): AppResult<Unit>
}
