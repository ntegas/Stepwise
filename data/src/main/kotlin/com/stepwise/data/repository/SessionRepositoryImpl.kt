package com.stepwise.data.repository

import androidx.room.withTransaction
import com.stepwise.core.common.AppResult
import com.stepwise.core.common.Clock
import com.stepwise.core.database.OutboxEntryEntity
import com.stepwise.core.database.SessionDao
import com.stepwise.core.database.SessionSyncDao
import com.stepwise.core.database.StepwiseDatabase
import com.stepwise.core.database.toDomain
import com.stepwise.core.database.toEntity
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.SessionMetricValue
import com.stepwise.core.model.UserId
import com.stepwise.data.runCatchingDatabaseCall
import com.stepwise.data.sync.toSyncPayloadJson
import com.stepwise.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The first (and so far only — DEV-008, `/PROJECT_STATE.md`'s findings) repository that enqueues
 * an outbox row on every upsert, in the same Room transaction as the entity write itself
 * (`/ARCHITECTURE.md` §9.1) — chosen as the proof entity because Session is explicitly "the
 * single most product-critical invariant in the app" (`/ARCHITECTURE.md` §24) and is the one
 * entity that exercises the full progress-affecting CAS/conflict-copy path, not just the trivial
 * metadata-tier blind upsert. [database] (not just a bare `SessionDao`) is needed here
 * specifically because [androidx.room.withTransaction] is how two different DAOs (`SessionDao` +
 * `OutboxDao`) share one atomic transaction — a single `@Transaction`-annotated DAO method can't
 * span two DAOs.
 *
 * **Real BLOCKER found and fixed during DEV-008 review** (adversarial reviewer, reproduced live
 * against Postgres): an earlier draft always enqueued `expectedVersion = null`, meaning every
 * write — including a retry of a stale payload after another device had already tombstoned or
 * edited the row — took the blind-upsert path forever. The fix: look up the row's *existing*
 * local state **before** overwriting it. If no local row exists yet, this really is a first-ever
 * write and `expectedVersion = null` (upsert) is correct. If one exists, its own `sync.version`
 * — which only ever advances via a server-confirmed push (see [com.stepwise.core.sync.SyncEngine]
 * `PushOutcome.Success.confirmedVersion`, written back by whatever drains the outbox) — becomes
 * the compare-and-swap filter, so a stale retry correctly loses to whatever actually landed on
 * the server in between, instead of silently overwriting it.
 *
 * **Real CRITICAL found and fixed during DEV-008 review** (architecture + logic reviewers): an
 * earlier draft enqueued an outbox row for the `Session` header only — every `SessionMetricValue`
 * (the actual logged numbers) never left the device at all. Now one outbox row is enqueued per
 * metric value too, in the same transaction, each going through the same existing-row lookup.
 */
class SessionRepositoryImpl(
    private val database: StepwiseDatabase,
    private val clock: Clock,
) : SessionRepository {
    private val dao: SessionDao get() = database.sessionDao()
    private val syncDao: SessionSyncDao get() = database.sessionSyncDao()

    override fun observeAll(userId: UserId): Flow<List<Session>> =
        dao.observeAll(userId.value).map { entities -> entities.map { it.toDomain() } }

    override fun observeForSource(
        sourceType: ExecutableKind,
        sourceId: String,
    ): Flow<List<Session>> =
        dao.observeForSource(sourceType, sourceId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByClientEventId(clientEventId: String): AppResult<Session?> =
        runCatchingDatabaseCall { dao.getByClientEventId(clientEventId)?.toDomain() }

    override suspend fun getById(id: SessionId): AppResult<Session?> =
        runCatchingDatabaseCall { syncDao.getById(id.value)?.toDomain() }

    override suspend fun getMetricValuesForSession(sessionId: SessionId): AppResult<List<SessionMetricValue>> =
        runCatchingDatabaseCall { dao.getMetricValuesForSession(sessionId.value).map { it.toDomain() } }

    override suspend fun upsertSessionWithMetricValues(
        session: Session,
        metricValues: List<SessionMetricValue>,
    ): AppResult<Unit> =
        runCatchingDatabaseCall {
            database.withTransaction {
                // Read existing local state INSIDE this transaction, not before calling it - a
                // real HIGH found during DEV-009 review (adversarial reviewer): an earlier draft
                // let every caller (Use Cases) do their own getById BEFORE this transaction, then
                // bake that version into the Session/SessionMetricValue object passed in here.
                // That pre-read can go stale between the caller's read and this transaction
                // actually running (a concurrent write to the same row, or an outbox drain's
                // updateConfirmedVersion landing in between) - and since the OLD code wrote
                // whatever version the caller's object carried straight to the local row, a
                // genuinely newer version could get silently stomped back down, corrupting this
                // device's own future CAS checks for that row. Fixed by never trusting the
                // caller's own sync.version for the actual write - only this fresh, in-transaction
                // read ever decides the version column, the same single source of truth already
                // used for the outbox's expectedVersion.
                val existingSessionVersion = syncDao.getById(session.id.value)?.sync?.version
                val existingMetricValueVersions =
                    metricValues.associate { it.id.value to syncDao.getMetricValueById(it.id.value)?.sync?.version }

                val sessionToWrite = session.copy(sync = session.sync.copy(version = existingSessionVersion ?: 1))
                val metricValuesToWrite =
                    metricValues.map { metricValue ->
                        val version = existingMetricValueVersions[metricValue.id.value] ?: 1
                        metricValue.copy(sync = metricValue.sync.copy(version = version))
                    }

                dao.upsertSessionWithMetricValues(sessionToWrite.toEntity(), metricValuesToWrite.map { it.toEntity() })

                val outbox = database.outboxDao()
                outbox.enqueue(
                    OutboxEntryEntity(
                        tableName = "sessions",
                        entityId = session.id.value,
                        payload = session.toSyncPayloadJson(),
                        expectedVersion = existingSessionVersion,
                        isProgressAffecting = true,
                        ownerUserId = session.userId.value,
                        createdAt = clock.now(),
                    ),
                )
                for (metricValue in metricValues) {
                    outbox.enqueue(
                        OutboxEntryEntity(
                            tableName = "session_metric_values",
                            entityId = metricValue.id.value,
                            payload = metricValue.toSyncPayloadJson(),
                            expectedVersion = existingMetricValueVersions[metricValue.id.value],
                            isProgressAffecting = true,
                            ownerUserId = session.userId.value,
                            createdAt = clock.now(),
                        ),
                    )
                }
            }
        }
}
