package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.LifeArea
import com.stepwise.core.model.LifeAreaId
import com.stepwise.core.model.UserId
import com.stepwise.core.model.Vision
import com.stepwise.core.model.VisionId
import kotlinx.coroutines.flow.Flow

/**
 * Owned by `:domain`, implemented in `:data` (ARCHITECTURE.md §4) — nothing above this
 * interface ever sees Room directly. Reads are a plain [Flow]: a local Room-backed read
 * doesn't fail mid-stream in normal operation, so wrapping every emission in [AppResult]
 * would be ceremony with no real benefit; a genuine read failure (e.g. a corrupted
 * database) still surfaces as a thrown exception from the [Flow], which a collector can
 * `catch` same as any other `Flow`. Mutations are `suspend fun ... : AppResult<Unit>`
 * since a write can genuinely fail (constraint violation, disk I/O) and the caller must
 * react to that explicitly rather than have it thrown as an unchecked exception.
 *
 * Deliberately no separate delete method, here or on any sibling Repository interface:
 * per `/DATA_MODEL.md`'s tombstone model, a delete is `upsert` with `SyncMetadata.deletedAt`
 * set — the same single write path, not a second one. Stated explicitly (`/ANTI_ERROR_STANDARD.md`
 * §5) rather than left to be inferred from the absence of a `delete` method.
 */
interface VisionRepository {
    fun observeAll(userId: UserId): Flow<List<Vision>>

    suspend fun getById(id: VisionId): AppResult<Vision?>

    suspend fun upsert(vision: Vision): AppResult<Unit>
}

interface LifeAreaRepository {
    fun observeAll(userId: UserId): Flow<List<LifeArea>>

    suspend fun getById(id: LifeAreaId): AppResult<LifeArea?>

    suspend fun upsert(lifeArea: LifeArea): AppResult<Unit>
}
