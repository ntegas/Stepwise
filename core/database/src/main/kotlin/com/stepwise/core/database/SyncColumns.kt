package com.stepwise.core.database

import com.stepwise.core.model.SyncMetadata
import kotlin.time.Instant

/**
 * The Room column shape of `:core:model`'s [SyncMetadata] — `@Embedded` into every entity
 * that carries it, so the 3 columns aren't hand-repeated per entity
 * (`DEVELOPMENT_PROTOCOL.md` centralization). Deliberately does **not** include a
 * `sync_status` column: per `SyncMetadata`'s own KDoc, that's LOCAL STATE tracked by the
 * outbox (DEV-008's job), not a property of the entity row itself.
 */
data class SyncColumns(
    val updatedAt: Instant,
    val version: Int,
    val deletedAt: Instant?,
)

fun SyncColumns.toDomain(): SyncMetadata = SyncMetadata(updatedAt = updatedAt, version = version, deletedAt = deletedAt)

fun SyncMetadata.toColumns(): SyncColumns = SyncColumns(updatedAt = updatedAt, version = version, deletedAt = deletedAt)
