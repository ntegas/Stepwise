package com.stepwise.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * A Goal (concept §9–13, `/DATA_MODEL.md`'s `goals`). All fields **CANONICAL** except [id]
 * and [sync] — including [type] and [progressMode], both set at creation and changed only by
 * a deliberate user edit, never computed.
 *
 * **Deliberately not a field here**: `current_value`. It is **DERIVED** — computed live by the
 * `goal_progress` view (`/DATA_MODEL.md`'s "Goal progress is a view, not a column", decision
 * §62) from `sessions`/`session_metric_values`/`progress_events`, mirrored on-device by
 * `:core:calculation` (`/ARCHITECTURE.md` §12). Modeling it as a stored field here would
 * reintroduce exactly the "independently-maintained number that can desync" §62 forbids —
 * callers read a Goal's progress through the calculation engine, never off this type.
 *
 * @param lifeAreaId nullable — a Goal need not belong to a Life Area.
 * @param projectId nullable — a Goal need not belong to a Project.
 * @param targetValue meaning depends on [type]/[progressMode] (a target amount, a frequency
 *   count, ...); null when [progressMode] is [GoalProgressMode.MILESTONES], [GoalProgressMode.TASKS],
 *   or [GoalProgressMode.MANUAL], none of which compute against a single numeric target.
 * @param unit a free-form display label ("km", "sessions", "$") paired with [targetValue] —
 *   distinct from [Metric.canonicalUnit]: a Goal's own `unit` is what a client shows the user,
 *   while [Metric] is the catalog Sessions actually record against; the two are reconciled by
 *   whichever [GoalActivityLink] feeds this Goal, not by this field.
 * @param priority a plain ordinal (higher = more important); not a locked product decision —
 *   revisable the same way DEV-003's `minSdk` pin was, not escalated as architecture.
 */
@Suppress("LongParameterList") // A real domain entity's genuine field count, not a function signature smell.
data class Goal(
    val id: GoalId,
    val userId: UserId,
    val lifeAreaId: LifeAreaId?,
    val projectId: ProjectId?,
    val title: String,
    val type: GoalType,
    val progressMode: GoalProgressMode,
    val targetValue: Double?,
    val unit: String?,
    val deadline: LocalDate?,
    val priority: Int?,
    val category: String?,
    val description: String?,
    val status: LifecycleStatus,
    val createdAt: Instant,
    val sync: SyncMetadata,
)

/**
 * An optional stage of a Goal (concept §14) — all fields **CANONICAL** except [id]/[sync].
 * [weight] + ([isBinary] xor [progressPercent]) implement the [GoalProgressMode.MILESTONES]
 * weighted-completion formula (`/ARCHITECTURE.md` §6).
 *
 * @param weight this Milestone's share of the parent Goal's total completion.
 * @param isBinary true = a done/not-done Milestone, read via [completed]; false = a partially
 *   gradable Milestone, read via [progressPercent].
 * @param completed meaningful only when [isBinary]; ignored otherwise.
 * @param progressPercent meaningful only when `!isBinary` (0.0–100.0); null otherwise.
 */
data class Milestone(
    val id: MilestoneId,
    val goalId: GoalId,
    val title: String,
    val sortOrder: Int,
    val weight: Double,
    val isBinary: Boolean,
    val completed: Boolean,
    val progressPercent: Double?,
    val sync: SyncMetadata,
)

/**
 * An optional grouping of Tasks under a Goal (concept §15) — all fields **CANONICAL** except
 * [id]/[sync]. [archived] feeds the Archive view (`/DATA_MODEL.md`'s "Inbox, Overdue, Archive").
 */
data class Project(
    val id: ProjectId,
    val userId: UserId,
    val goalId: GoalId?,
    val lifeAreaId: LifeAreaId?,
    val title: String,
    val archived: Boolean,
    val sync: SyncMetadata,
)
