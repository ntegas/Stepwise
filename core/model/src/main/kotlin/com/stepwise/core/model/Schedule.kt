package com.stepwise.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The shapes a [RecurrenceRule] can take (`/ARCHITECTURE.md` §13: "daily, weekly, selected
 * weekdays, every-N-days, monthly, and a bounded custom form"). **CANONICAL** on
 * [RecurrenceRule.pattern].
 */
sealed class RecurrencePattern {
    data object Daily : RecurrencePattern()

    data class Weekly(
        val daysOfWeek: Set<DayOfWeek>,
    ) : RecurrencePattern()

    data class EveryNDays(
        val intervalDays: Int,
    ) : RecurrencePattern()

    data class Monthly(
        val dayOfMonth: Int,
    ) : RecurrencePattern()

    /** The "bounded custom form" — an RRULE-style expression, not free-form text. */
    data class Custom(
        val rrule: String,
    ) : RecurrencePattern()
}

/**
 * One recurrence engine for Task, Activity, and Habit (concept §30/§33, `/ARCHITECTURE.md` §13)
 * — an owner-polymorphic rule, not three separate rule types. All fields **CANONICAL** except
 * [id]/[sync].
 *
 * @param ownerId intentionally a raw `String`, not one of [TaskId]/[ActivityId]/[HabitId]: the
 *   owner is polymorphic across all three, matching `/DATA_MODEL.md`'s `owner_type`/`owner_id`
 *   pair directly — [ownerType] is the discriminant a mapper (DEV-005) uses to resolve this
 *   back to the correct typed ID, rather than this type carrying three optional typed-ID fields
 *   where at most one is ever set.
 */
data class RecurrenceRule(
    val id: RecurrenceRuleId,
    val userId: UserId,
    val ownerType: ExecutableKind,
    val ownerId: String,
    val pattern: RecurrencePattern,
    val time: LocalTime?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val sync: SyncMetadata,
)

/**
 * A materialized occurrence — a row that exists only because it needs to carry individual
 * state (`/ARCHITECTURE.md` §13: completion, an edit to that one instance, a reschedule, a
 * skip). A **virtual** occurrence (the common case: an unmodified date the [RecurrenceRule]
 * implies) is not a row at all and has no [RecurrenceOccurrence] instance — it's computed
 * on read (`OccurrenceResolution`, DEV-010's job, not this one). All fields **CANONICAL**
 * except [id]/[sync] — including [id] itself in one sense: it's still domain-meaningful data
 * (not opaque metadata) despite being *derivable* from [recurrenceRuleId]/[occurrenceDate] via
 * [OccurrenceIdentity] rather than freely chosen, which is exactly what makes two independently
 * materializing devices converge on one row (decision B9) instead of needing a separate dedup step.
 *
 * @param sessionId set once this occurrence has been executed — the occurrence carries the
 *   per-instance *schedule* state, the linked [Session] carries the *execution* fact
 *   (`/ARCHITECTURE.md` §13); null before execution.
 */
data class RecurrenceOccurrence(
    val id: RecurrenceOccurrenceId,
    val recurrenceRuleId: RecurrenceRuleId,
    val occurrenceDate: LocalDate,
    val rescheduledToDate: LocalDate?,
    val skipped: Boolean,
    val sessionId: SessionId?,
    val sync: SyncMetadata,
)
