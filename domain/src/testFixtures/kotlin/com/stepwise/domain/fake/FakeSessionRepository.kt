package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Stores Sessions and their metric values exactly the way DEV-008's real transaction does:
 * [upsertSessionWithMetricValues] replaces the Session row and upserts each given metric value
 * by its own id, never deleting a metric value not present in the given list (`:core:database`'s
 * `@Upsert` has the same "add or replace, never delete the rest" behavior) — so a test asserting
 * idempotent re-completion sees the same real collapsing behavior a Room-backed run would.
 */
class FakeSessionRepository : SessionRepository {
    private val sessions = MutableStateFlow<Map<String, Session>>(emptyMap())
    private val metricValues = MutableStateFlow<Map<String, SessionMetricValue>>(emptyMap())

    /** How many distinct Session rows exist right now — the idempotency assertion a test wants. */
    val sessionCount: Int get() = sessions.value.size

    /** How many times [upsertSessionWithMetricValues] has actually been called. */
    var upsertCallCount: Int = 0
        private set

    override fun observeAll(userId: UserId): Flow<List<Session>> =
        sessions.map { all -> all.values.filter { it.userId == userId && it.sync.deletedAt == null } }

    override fun observeForSource(
        sourceType: ExecutableKind,
        sourceId: String,
    ): Flow<List<Session>> =
        sessions.map { all ->
            all.values.filter {
                it.sourceType == sourceType && it.sourceId == sourceId && it.sync.deletedAt == null
            }
        }

    override suspend fun getByClientEventId(clientEventId: String): AppResult<Session?> =
        AppResult.Success(sessions.value.values.firstOrNull { it.clientEventId == clientEventId })

    override suspend fun getById(id: SessionId): AppResult<Session?> = AppResult.Success(sessions.value[id.value])

    override suspend fun getMetricValuesForSession(sessionId: SessionId): AppResult<List<SessionMetricValue>> =
        AppResult.Success(
            metricValues.value.values.filter { it.sessionId == sessionId && it.sync.deletedAt == null },
        )

    override suspend fun upsertSessionWithMetricValues(
        session: Session,
        metricValues: List<SessionMetricValue>,
    ): AppResult<Unit> {
        upsertCallCount += 1
        sessions.value = sessions.value + (session.id.value to session)
        this.metricValues.value = this.metricValues.value + metricValues.associateBy { it.id.value }
        return AppResult.Success(Unit)
    }
}
