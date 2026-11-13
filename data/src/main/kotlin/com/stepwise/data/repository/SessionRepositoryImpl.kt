package com.stepwise.data.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.database.SessionDao
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val dao: SessionDao,
) : SessionRepository {
    override fun observeAll(userId: UserId): Flow<List<Session>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override fun observeForSource(
        sourceType: ExecutableKind,
        sourceId: String,
    ): Flow<List<Session>> =
        dao.observeForSource(sourceType, sourceId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByClientEventId(clientEventId: String): AppResult<Session?> =
        runCatchingDatabaseCall { dao.getByClientEventId(clientEventId)?.toDomain() }

    override suspend fun getMetricValuesForSession(sessionId: SessionId): AppResult<List<SessionMetricValue>> =
        runCatchingDatabaseCall { dao.getMetricValuesForSession(sessionId.value).map { it.toDomain() } }

    override suspend fun upsertSessionWithMetricValues(
        session: Session,
        metricValues: List<SessionMetricValue>,
    ): AppResult<Unit> =
        runCatchingDatabaseCall {
            dao.upsertSessionWithMetricValues(session.toEntity(), metricValues.map { it.toEntity() })
        }
}
