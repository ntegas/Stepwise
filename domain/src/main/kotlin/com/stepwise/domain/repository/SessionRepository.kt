package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeAll(userId: UserId): Flow<List<Session>>

    fun observeForSource(
        sourceType: ExecutableKind,
        sourceId: String,
    ): Flow<List<Session>>

    suspend fun getByClientEventId(clientEventId: String): AppResult<Session?>

    suspend fun getMetricValuesForSession(sessionId: SessionId): AppResult<List<SessionMetricValue>>

    /**
     * The one multi-entity write this layer demonstrates (`/ROADMAP.md` DEV-006's
     * "transaction coordination") — delegates straight to `SessionDao`'s own `@Transaction`
     * method (`/core/database/.../SessionEntities.kt`), so the Session row and every one of
     * its metric values still commit together or not at all; this repository method adds
     * nothing but the domain↔entity mapping and [AppError.Database] wrapping, it does not
     * re-implement the transaction boundary that already exists at the Room layer.
     */
    suspend fun upsertSessionWithMetricValues(
        session: Session,
        metricValues: List<SessionMetricValue>,
    ): AppResult<Unit>
}
