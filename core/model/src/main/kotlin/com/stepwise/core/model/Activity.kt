package com.stepwise.core.model

/**
 * An ongoing activity, independent of any Goal (concept §19–20) — all fields **CANONICAL**
 * except [id]/[sync]. No `defaultGoalId` field: Activity↔Goal is many-to-many, each link
 * scoped to a specific [Metric] — modeled by [GoalActivityLink], not a single FK on this type
 * (concept §21, `/DATA_MODEL.md`).
 */
data class Activity(
    val id: ActivityId,
    val userId: UserId,
    val lifeAreaId: LifeAreaId?,
    val title: String,
    val icon: String?,
    val archived: Boolean,
    val sync: SyncMetadata,
)

/**
 * A regularity rule, distinct from [Activity] (concept §31) — all fields **CANONICAL** except
 * [id]/[sync]. A Habit's own Goal links go through [GoalActivityLink] too
 * ([ProgressSourceKind.HABIT]) — the same fan-out mechanism Activities use, one path not two
 * (`/DATA_MODEL.md`).
 *
 * @param activityId nullable — the "what" this Habit is a regularity rule for; a Habit need
 *   not be tied to a pre-existing Activity.
 * @param targetValue the per-period amount the [metricId] must reach for this Habit to count
 *   as satisfied (e.g. "3" for a "3x/week" habit measured in [Metric] `count`).
 */
data class Habit(
    val id: HabitId,
    val userId: UserId,
    val activityId: ActivityId?,
    val title: String,
    val targetValue: Double,
    val metricId: MetricId,
    val archived: Boolean,
    val sync: SyncMetadata,
)
