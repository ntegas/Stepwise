package com.stepwise.core.model

/*
 * One typed ID value class per entity (DEV-004), all wrapping the same representation
 * IdGenerator (com.stepwise.core.common.IdGenerator) already produces — a stringified
 * UUID — so a GoalId and a TaskId can never be accidentally interchanged at compile
 * time, without introducing a second ID representation for this module to depend on
 * :core:common for. :core:model stays dependency-free (ARCHITECTURE.md §5/§6): these
 * are constructed from a raw String an outer layer already generated via IdGenerator,
 * not generated here.
 */

/** The Supabase Auth user this row belongs to — every table carries this (`/DATA_MODEL.md`). */
@JvmInline
value class UserId(
    val value: String,
)

@JvmInline
value class VisionId(
    val value: String,
)

@JvmInline
value class LifeAreaId(
    val value: String,
)

@JvmInline
value class GoalId(
    val value: String,
)

@JvmInline
value class MilestoneId(
    val value: String,
)

@JvmInline
value class ProjectId(
    val value: String,
)

@JvmInline
value class TaskId(
    val value: String,
)

@JvmInline
value class ActivityId(
    val value: String,
)

@JvmInline
value class HabitId(
    val value: String,
)

@JvmInline
value class RecurrenceRuleId(
    val value: String,
)

/** Not client-generated at creation time like the others — see [OccurrenceIdentity] (B9). */
@JvmInline
value class RecurrenceOccurrenceId(
    val value: String,
)

@JvmInline
value class SessionId(
    val value: String,
)

@JvmInline
value class SessionMetricValueId(
    val value: String,
)

@JvmInline
value class MetricId(
    val value: String,
)

@JvmInline
value class GoalActivityLinkId(
    val value: String,
)

/**
 * The full [Reminder] entity is DEV-027's (Notifications) job — this ID exists now only so
 * [Task]/[Habit] can reference one without pulling that entity's shape into DEV-004's scope.
 */
@JvmInline
value class ReminderId(
    val value: String,
)
