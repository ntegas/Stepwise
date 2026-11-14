package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.RecurrenceOccurrenceDao
import com.stepwise.core.database.RecurrenceRuleDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.UserId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecurrenceRuleRepositoryImpl(
    private val dao: RecurrenceRuleDao,
) : RecurrenceRuleRepository {
    override fun observeAll(userId: UserId): Flow<List<RecurrenceRule>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override fun observeForOwner(
        ownerType: ExecutableKind,
        ownerId: String,
    ): Flow<List<RecurrenceRule>> =
        dao.observeForOwner(ownerType, ownerId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: RecurrenceRuleId): AppResult<RecurrenceRule?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(rule: RecurrenceRule): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(rule.toEntity()) }
}

class RecurrenceOccurrenceRepositoryImpl(
    private val dao: RecurrenceOccurrenceDao,
) : RecurrenceOccurrenceRepository {
    override fun observeForRule(ruleId: RecurrenceRuleId): Flow<List<RecurrenceOccurrence>> =
        dao.observeForRule(ruleId.value).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: RecurrenceOccurrenceId): AppResult<RecurrenceOccurrence?> =
        runCatchingDatabaseCall { dao.getById(id.value)?.toDomain() }

    override suspend fun upsert(occurrence: RecurrenceOccurrence): AppResult<Unit> =
        runCatchingDatabaseCall { dao.upsert(occurrence.toEntity()) }
}
