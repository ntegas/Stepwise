package com.stepwise.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * **Covers four of the five sources `/ARCHITECTURE.md` §14 actually names** ("scheduled Task
 * occurrences, planned Activity occurrences, Habit occurrences, deadlines, Overdue items") —
 * task/activity/habit/overdue, not a richer taxonomy invented here. Found inaccurate at DEV-014
 * review (architecture reviewer, HIGH): an earlier version of this KDoc claimed these were "the
 * four sources," miscounting §14's own list. **`Task.deadline` is the missing fifth source,
 * genuinely not implemented here** — no canonical doc specifies a deadline-only Task's session-
 * date semantics (what date a completion keys to when `date` itself is null), how it interacts
 * with an already-set `date`, or whether a lead-time window applies before the deadline itself —
 * `/ROADMAP.md`'s DEV-014 one-liner omits it entirely while `/ARCHITECTURE.md` §14 names it,
 * exactly the kind of cross-doc conflict `/ANTI_ERROR_STANDARD.md` §4 says to surface rather than
 * silently resolve. Tracked in `/PROJECT_STATE.md`'s DEV-014 findings and VERIFICATION DEBT for a
 * product-owner decision, not invented unilaterally here.
 */
enum class TodayItemKind {
    TASK,
    ACTIVITY,
    HABIT,
    OVERDUE,
}

/**
 * One row on the Today agenda (`/ARCHITECTURE.md` §14, `/ROADMAP.md` DEV-014) — the single
 * unified shape [GetTodayAgendaUseCase][com.stepwise.domain.usecase.GetTodayAgendaUseCase]
 * reduces every Task/Activity/Habit occurrence to, so `:feature:today`'s UI (and later the
 * Widget, `/ARCHITECTURE.md` §19.7) never needs to know whether a given item came from a plain
 * [Task.date] or from resolving a [RecurrenceRule] occurrence, or which of the three owner kinds
 * it started life as beyond [sourceType]/[kind].
 *
 * @param sourceId the owning Task/Activity/Habit's raw id string (matching [sourceType]) — not a
 *   typed id directly, the same polymorphic-owner reasoning [RecurrenceRule.ownerId]/
 *   [Session.sourceId] already use, since this type ranges over all three.
 * @param goalImpactScore the ranking input `/ARCHITECTURE.md` §14 names ("ranking uses each
 *   item's Goal Impact Score where present") — null for [TodayItemKind.ACTIVITY]/[TodayItemKind.HABIT]
 *   (no such field exists on [Activity]/[Habit] yet, confirmed absent from both types' own field
 *   lists), not a missing value this type failed to populate.
 * @param isCompleted whether anything has already been recorded against this item today — a
 *   [Session] exists for a one-off item, or the resolved occurrence carries a `sessionId`/is
 *   marked skipped for a recurring one. Deliberately coarser than [ExecutionStatus] (DONE vs
 *   PARTIAL vs MISSED aren't distinguished here): nothing downstream needs that finer grain yet,
 *   and [GetTodayAgendaUseCase][com.stepwise.domain.usecase.GetTodayAgendaUseCase]'s own KDoc
 *   discloses this as a deliberate scope decision, not an oversight.
 * @param recurrenceRuleId null for a one-off item (sourced from [Task.date] directly); the owning
 *   [RecurrenceRule]'s id for a recurring one. Added at DEV-014 review (logic reviewer, HIGH):
 *   without this, nothing downstream (DEV-015's "Today → item → execute" flow, the very next task
 *   to consume this type) could reconstruct the right `CompletionTarget` to actually complete the
 *   item this row represents.
 * @param occurrenceDate null for a one-off item; for a recurring one, the occurrence's own
 *   identity-bearing date (`/ARCHITECTURE.md` §13's "identity/dedup keys on occurrenceDate, never
 *   displayDate") — **not necessarily equal to [date]**, which is always the display day this row
 *   appears under (today). A rescheduled-into-today occurrence has [occurrenceDate] somewhere in
 *   the past and [date] = today; completing it correctly needs the former, not the latter.
 */
data class TodayItem(
    val kind: TodayItemKind,
    val sourceType: ExecutableKind,
    val sourceId: String,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val isCompleted: Boolean,
    val goalImpactScore: Int?,
    val recurrenceRuleId: RecurrenceRuleId?,
    val occurrenceDate: LocalDate?,
)

/**
 * The one unified read model `/ARCHITECTURE.md` §14 requires `:feature:today` and, later, the
 * Widget (`/ARCHITECTURE.md` §19.6/§19.7) to consume — both must render from exactly this shape,
 * never re-deriving it themselves, per that section's own "App and Widget show identical Today
 * state" acceptance criterion (AC4).
 *
 * @param focus the single highest-ranked, not-yet-[TodayItem.isCompleted] item in [items] — the
 *   "Focus: Finish payment integration" line `/PRODUCT_CANON.md` §9's own mockup shows; null once
 *   every item for today is already completed, or when there is nothing scheduled at all.
 * @param items today's own scheduled items — [TodayItemKind.OVERDUE] items are never mixed into
 *   this list (`/PRODUCT_CANON.md` §18: Overdue is its own block, never auto-merged into today's
 *   list), they live in [overdue] instead.
 * @param completedCount out of [totalCount] — the "Progress: 3/5" line `/PRODUCT_CANON.md` §9's
 *   mockup shows. Counts [items] only, never [overdue] — an overdue item belongs to a day that
 *   already passed, not to today's own completion count.
 */
data class TodayAgenda(
    val focus: TodayItem?,
    val items: List<TodayItem>,
    val overdue: List<TodayItem>,
    val completedCount: Int,
    val totalCount: Int,
)
