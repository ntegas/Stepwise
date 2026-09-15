package com.stepwise.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * A Task (concept §16–18). Almost every field beyond [title] is optional, per §16. All fields
 * **CANONICAL** except [id]/[sync] — with two deliberate, explicitly-resolved omissions below.
 *
 * **A real inconsistency found and resolved here (`/ANTI_ERROR_STANDARD.md` §2)**:
 * `/DATA_MODEL.md`'s task field list names `actual_result` and `status` directly as columns,
 * but `/ARCHITECTURE.md` §6's decision **B1** states plainly that "Task has no independently-
 * editable actual-execution field" and that anything the UI shows as "Task Actual" is a read
 * projection from the linked [Session]/[SessionMetricValue] rows, never a second place that
 * value can be entered. Treated the same way `/DATA_MODEL.md` already treats `goals.current_value`
 * (§62 — a view, not a column): both are **omitted here as stored fields** and are instead
 * **DERIVED** — computed from this Task's linked Sessions (absent any Session, the derived
 * status reads as "not yet executed", a client-computed default, not a stored value). B1 is an
 * explicit, later, named architectural decision and `/ARCHITECTURE.md` is what `/DATA_MODEL.md`
 * is meant to conform to (`/ARCHITECTURE.md` §5's own doc-hierarchy statement) — this resolves
 * the tension in B1's favor rather than leaving both fields ambiguously ownable, and
 * `/DATA_MODEL.md`'s task section needs the same correction, tracked in this DEV task's findings.
 *
 * @param date null = Inbox/Unscheduled (concept §17).
 * @param goalImpactScore concept §50; `/DATA_MODEL.md` lists it as a plain column with no
 *   view/derivation caveat (unlike `current_value`), so modeled **CANONICAL** here — revisit
 *   if DEV-011/DEV-019 (Goals Domain / Progress Calculation) finds it should instead be computed.
 * @param plannedResult genuinely Task-owned (`/ARCHITECTURE.md` §6) — unlike actual execution,
 *   nothing else can set the plan for this Task.
 */
@Suppress("LongParameterList") // A real domain entity's genuine field count, not a function signature smell.
data class Task(
    val id: TaskId,
    val userId: UserId,
    val goalId: GoalId?,
    val projectId: ProjectId?,
    val lifeAreaId: LifeAreaId?,
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val deadline: LocalDate?,
    val durationMinutes: Int?,
    val priority: Int?,
    val reminderId: ReminderId?,
    val metricId: MetricId?,
    val plannedResult: Double?,
    val goalImpactScore: Int?,
    val archived: Boolean,
    val createdAt: Instant,
    val sync: SyncMetadata,
)
