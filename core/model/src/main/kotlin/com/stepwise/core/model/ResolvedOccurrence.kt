package com.stepwise.core.model

import kotlinx.datetime.LocalDate

/**
 * One occurrence as DEV-010's resolver presents it to a caller — merging a virtual date
 * [occurrenceDatesIn] generates with any materialized [RecurrenceOccurrence] override at that
 * date. Deliberately a separate type, not a reuse of [RecurrenceOccurrence] directly: a virtual
 * occurrence has no row and no [SyncMetadata] that was ever actually persisted — [id] here is
 * always [OccurrenceIdentity.deterministicId]'s *computed* value, materialized or not, so a
 * caller never needs to care which case it is to use the id consistently (the exact "same IDs
 * app/widget/sync" guarantee `/ROADMAP.md`'s DEV-010 acceptance criteria names).
 *
 * @param occurrenceDate the rule-implied (or originally-materialized) date — identity/dedup
 *   stays keyed on this, never on [rescheduledToDate], so a reschedule never produces a second,
 *   independently-identified occurrence for the same logical event.
 * @param rescheduledToDate non-null only for a materialized, rescheduled occurrence — a real,
 *   previously undocumented gap found while scoping this task: `/ARCHITECTURE.md`/`/DATA_MODEL.md`
 *   describe [RecurrenceOccurrence.rescheduledToDate] as a field but never say which date a
 *   range query should surface it under. Resolved here, not left ambiguous: this type keeps
 *   both dates, and [displayDate] is the one answer for "what date should a UI show this at."
 * @param isMaterialized false for a pure virtual occurrence (no [RecurrenceOccurrence] row
 *   exists for it yet); true for one backed by an actual row, whether or not that row's own
 *   [RecurrenceOccurrence.occurrenceDate] still falls inside the resolver's own generated date
 *   list — a materialized row is never dropped just because the rule no longer implies its date
 *   (ADR-006: "nothing a user recorded ever silently vanishes").
 */
data class ResolvedOccurrence(
    val id: RecurrenceOccurrenceId,
    val recurrenceRuleId: RecurrenceRuleId,
    val occurrenceDate: LocalDate,
    val rescheduledToDate: LocalDate?,
    val skipped: Boolean,
    val sessionId: SessionId?,
    val isMaterialized: Boolean,
) {
    val displayDate: LocalDate get() = rescheduledToDate ?: occurrenceDate
}
