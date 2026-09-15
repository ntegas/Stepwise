package com.stepwise.core.model

/**
 * A Goal's shape (concept §11, `/DATA_MODEL.md`'s `goals.type`) — **CANONICAL**, set at
 * creation, changing it is a deliberate user edit, not a computed value.
 */
enum class GoalType {
    CUMULATIVE,
    QUANTITY,
    DISTANCE,
    FINANCIAL,
    TARGET,
    FREQUENCY,
    PERCENTAGE,
}

/**
 * How a Goal's progress is actually computed (`/ARCHITECTURE.md` §6, decision B6) — **CANONICAL**.
 * Exactly one mode per Goal, never blended. This governs which `goal_progress` view branch
 * (`/DATA_MODEL.md`) applies; it is a modeling choice the user/product makes per Goal, not
 * derived from [GoalType] (a [GoalType.CUMULATIVE] goal is normally [METRIC], but the two axes
 * are independent so an unusual pairing is still representable).
 */
enum class GoalProgressMode {
    METRIC,
    TARGET_VALUE,
    FREQUENCY,
    MILESTONES,
    TASKS,
    MANUAL,
}

/**
 * A Goal's own lifecycle (concept §13) — **CANONICAL**, independent of any Session/Task
 * execution status (`/ARCHITECTURE.md` §6's "two status axes, never merged"). Only [Goal]
 * carries this: `/DATA_MODEL.md`'s field lists give every other entity (Task, Activity, Habit,
 * Project) a simple `archived: Boolean` instead of this richer 4-state cycle — a Task doesn't
 * have its own multi-week "paused" concept the way a Goal does, so representing them
 * differently follows the concrete field shapes in `/DATA_MODEL.md` rather than applying one
 * enum uniformly. Stated here explicitly since `/ARCHITECTURE.md` §6's prose could be read as
 * implying Task also gets this exact enum; `/DATA_MODEL.md`'s per-entity field list is treated
 * as the more concrete, authoritative source for exact shape (`/ARCHITECTURE.md` §5's own
 * ordering: it explicitly defers "exact field types" to the Canonical Data Model layer).
 */
enum class LifecycleStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED,
}

/**
 * The outcome of one occurrence/Session (concept §25) — **CANONICAL** on [Session] (what
 * actually happened) and **DERIVED** everywhere else it's shown (a [Task]'s displayed status
 * is a read projection off its linked Session(s), `/ARCHITECTURE.md` §6 — Task has no
 * independently-editable actual-execution field, decision B1).
 */
enum class ExecutionStatus {
    DONE,
    PARTIAL,
    MISSED,
    RESCHEDULED,
    IN_PROGRESS,
    CANCELLED,
}

/**
 * The three kinds of thing that can carry a recurrence rule ([RecurrenceRule.ownerType]) or be
 * the source of a [Session] ([Session.sourceType]) — **CANONICAL**. `/DATA_MODEL.md` names
 * these two relationships separately (`schedules.owner_type`, `sessions.source_type`) but both
 * range over exactly {Task, Activity, Habit} — deliberately consolidated into one enum here
 * (`DEVELOPMENT_PROTOCOL.md`'s centralization principle: one shape, not two identical ones)
 * rather than two enums with the same three cases.
 */
enum class ExecutableKind {
    TASK,
    ACTIVITY,
    HABIT,
}

/**
 * The two kinds of thing a [GoalActivityLink] can originate from (concept §21/§22) —
 * **CANONICAL**. Narrower than [ExecutableKind]: a bare Task has no Goal-metric link of its
 * own (a Task's Goal contribution flows through its own `goal_id`/`goal_impact_score`, not
 * through this fan-out mechanism, `/DATA_MODEL.md`), so this is its own, smaller enum rather
 * than reusing [ExecutableKind] and leaving `TASK` meaningless here.
 */
enum class ProgressSourceKind {
    ACTIVITY,
    HABIT,
}
