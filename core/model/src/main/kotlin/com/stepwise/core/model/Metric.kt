package com.stepwise.core.model

/**
 * A catalog entry for a measurable dimension (concept, `/DATA_MODEL.md`'s `metrics` —
 * `duration_minutes`, `distance_km`, `count`, `pages`, `amount_minor_units`, `weight_kg`, ...).
 * All fields **CANONICAL**.
 *
 * Deliberately has **no [UserId] and no [SyncMetadata]**: unlike every other entity in this
 * module, `/DATA_MODEL.md`'s `metrics` section is the one entity list that never mentions
 * `user_id` — read here as a genuinely global, shared reference catalog (the definition of
 * "distance in km" is the same fact for every account), not per-user data, so the blanket "every
 * table carries `user_id` and syncs" statement at the top of `/DATA_MODEL.md` is read as
 * describing the *domain* tables, not this one catalog table. Flagged explicitly since it's the
 * one deliberate exception to an otherwise-universal rule, not an oversight.
 *
 * @param key the stable catalog key (e.g. `"duration_minutes"`) every [Session]/[Goal]/[Habit]
 *   reference by [MetricId] points at.
 * @param canonicalUnit the unit values are always stored in for this metric (e.g. `"min"`) —
 *   display conversion (km↔mi, kg↔lb, currency symbol) happens client-side from the user's
 *   Settings unit preference (concept §58); a stored [SessionMetricValue] is always in this
 *   unit, never a display one, so analytics never has to guess which unit a historical row used.
 */
data class Metric(
    val id: MetricId,
    val key: String,
    val canonicalUnit: String,
)

/**
 * The mechanism behind "one Activity/Habit can update multiple Goals, each via a different
 * metric" (concept §21/§22, `/DATA_MODEL.md`). All fields **CANONICAL** except [id]/[sync].
 *
 * @param sourceId intentionally a raw `String`, not [ActivityId]/[HabitId] directly — same
 *   polymorphic-owner reasoning as [RecurrenceRule.ownerId]; [sourceType] is the discriminant.
 */
data class GoalActivityLink(
    val id: GoalActivityLinkId,
    val sourceType: ProgressSourceKind,
    val sourceId: String,
    val goalId: GoalId,
    val metricId: MetricId,
    val sync: SyncMetadata,
)
