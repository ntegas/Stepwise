package com.stepwise.core.model

import kotlin.time.Instant

/**
 * Free-text strategic statement (concept §5). All fields **CANONICAL** except [sync] and [id].
 *
 * @param lifeAreaId null = a whole-life vision, not scoped to one [LifeArea] (`/DATA_MODEL.md`).
 */
data class Vision(
    val id: VisionId,
    val userId: UserId,
    val lifeAreaId: LifeAreaId?,
    val content: String,
    val updatedAt: Instant,
    val sync: SyncMetadata,
)

/**
 * A user-defined, fully customizable life category (concept §6). All fields **CANONICAL**
 * except [sync] and [id]. [sortOrder]/[archived] are user-controlled display state, still
 * CANONICAL (the user's own arrangement is a fact to preserve, not something derived).
 */
data class LifeArea(
    val id: LifeAreaId,
    val userId: UserId,
    val name: String,
    val icon: String?,
    val category: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val sync: SyncMetadata,
)
