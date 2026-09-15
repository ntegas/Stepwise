package com.stepwise.core.model

/**
 * Free-text strategic statement (concept §5). All fields **CANONICAL** except [sync] and [id].
 *
 * Fixed at DEV-005 (`/ANTI_ERROR_STANDARD.md` §2): this originally also carried its own
 * `updatedAt: Instant`, duplicating [SyncMetadata.updatedAt] — `/DATA_MODEL.md`'s `visions`
 * field list has exactly one `updated_at` column, not two. "When was this last changed" is
 * already [sync]'s job; a Vision has no separate creation-vs-modification distinction worth
 * a second timestamp (unlike [Goal]/[Task]'s genuinely distinct `createdAt`).
 *
 * @param lifeAreaId null = a whole-life vision, not scoped to one [LifeArea] (`/DATA_MODEL.md`).
 */
data class Vision(
    val id: VisionId,
    val userId: UserId,
    val lifeAreaId: LifeAreaId?,
    val content: String,
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
