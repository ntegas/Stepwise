package com.stepwise.domain.usecase

import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.OccurrenceIdentity
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.ResolvedOccurrence
import com.stepwise.core.model.UserId
import com.stepwise.core.model.occurrenceDatesIn
import com.stepwise.domain.repository.RecurrenceOccurrenceRepository
import com.stepwise.domain.repository.RecurrenceRuleRepository
import kotlinx.datetime.LocalDate

/**
 * The one place a [RecurrenceRule] is turned into a concrete list of occurrences within a date
 * [range] (`/ROADMAP.md` DEV-010) — merging [com.stepwise.core.model.occurrenceDatesIn]'s
 * generated virtual dates with any materialized [RecurrenceOccurrence] rows, so a caller (the
 * future Today Engine, DEV-014; a future Calendar view) never re-implements this merge itself
 * ("нет дублирующих engines" — `/docs/dev-plan-detailed.md`'s own DEV-010 acceptance criterion).
 *
 * **Takes `(userId, ruleId)`, not a bare [RecurrenceRule], and verifies ownership itself** via
 * [RecurrenceRuleAccess] — a real MEDIUM found at DEV-010 review (adversarial + security
 * reviewers, independently): an earlier draft took a caller-supplied [RecurrenceRule] object
 * directly and trusted the caller to have already checked ownership, with no defense-in-depth
 * of its own — exactly the class of gap DEV-009's review already found and fixed once for
 * [CompleteOccurrenceUseCase]. Fixed the same way: this use case now owns its own check, the
 * same `RecurrenceRuleAccess` helper [SkipOccurrenceUseCase]/[CompleteOccurrenceUseCase] already
 * share, rather than relying on every future caller to remember to check first.
 *
 * **A materialized row is never dropped just because its own [RecurrenceOccurrence.occurrenceDate]
 * is no longer one the rule's current pattern would generate** (e.g. the rule was edited after
 * the row materialized) — ADR-006: "nothing a user recorded ever silently vanishes." The merged
 * result is the union of generated virtual dates and materialized dates within [range], not the
 * virtual dates alone with materialized rows merely overlaid on a subset of them.
 *
 * **A rescheduled occurrence is included whenever either its original date or its
 * [ResolvedOccurrence.displayDate] falls in [range]** — fixed at DEV-010 review (architecture +
 * logic reviewers, HIGH, independently): an earlier draft filtered materialized rows by
 * [RecurrenceOccurrence.occurrenceDate] alone, so a reschedule that moved an occurrence's
 * display date across the queried range's boundary could make it vanish from the range it was
 * moved *into*, or from the range it was moved *out of* — contradicting `/ARCHITECTURE.md` §13's
 * "Today and Plan/Calendar call the same occurrence-resolution function" and ADR-006 alike, not
 * a genuinely open product question this file gets to default on. Identity/dedup still keys on
 * [RecurrenceOccurrence.occurrenceDate] (never [ResolvedOccurrence.displayDate]) — only the
 * inclusion test was widened, not the identity scheme "same IDs app/widget/sync" depends on.
 */
class ResolveOccurrencesUseCase(
    private val recurrenceRuleRepository: RecurrenceRuleRepository,
    private val recurrenceOccurrenceRepository: RecurrenceOccurrenceRepository,
) {
    suspend fun execute(
        userId: UserId,
        ruleId: RecurrenceRuleId,
        range: ClosedRange<LocalDate>,
    ): AppResult<List<ResolvedOccurrence>> =
        RecurrenceRuleAccess.verifyOwnedRule(recurrenceRuleRepository, userId, ruleId).andThen { rule ->
            generateVirtualDates(rule, range).andThen { virtualDates ->
                recurrenceOccurrenceRepository.getAllForRule(rule.id).map { materialized ->
                    merge(rule.id, range, virtualDates, materialized)
                }
            }
        }

    // :core:model has no AppResult of its own (zero project dependencies, see its build.gradle.kts
    // and occurrenceDatesIn's own KDoc) - this is the one boundary that turns RecurrencePattern.
    // Custom's deliberately-narrow-scope IllegalArgumentException into an ordinary AppError, the
    // same justified-boundary-catch reasoning SyncEngine/DataErrorMapping already use.
    private fun generateVirtualDates(
        rule: RecurrenceRule,
        range: ClosedRange<LocalDate>,
    ): AppResult<List<LocalDate>> =
        try {
            AppResult.Success(rule.occurrenceDatesIn(range))
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.Validation(e.message ?: "Invalid recurrence pattern", field = "pattern"))
        }

    private fun merge(
        ruleId: RecurrenceRuleId,
        range: ClosedRange<LocalDate>,
        virtualDates: List<LocalDate>,
        materialized: List<RecurrenceOccurrence>,
    ): List<ResolvedOccurrence> {
        val relevant = materialized.filterNot { it.sync.isDeleted }.filter { it.isRelevantTo(range) }
        val byOccurrenceDate = relevant.associateBy { it.occurrenceDate }
        val allDates = (virtualDates.toSet() + byOccurrenceDate.keys).sorted()
        return allDates.map { date -> byOccurrenceDate[date]?.toResolved() ?: date.toVirtualResolved(ruleId) }
    }

    private fun RecurrenceOccurrence.isRelevantTo(range: ClosedRange<LocalDate>): Boolean =
        occurrenceDate in range || (rescheduledToDate ?: occurrenceDate) in range

    private fun RecurrenceOccurrence.toResolved(): ResolvedOccurrence =
        ResolvedOccurrence(
            id = id,
            recurrenceRuleId = recurrenceRuleId,
            occurrenceDate = occurrenceDate,
            rescheduledToDate = rescheduledToDate,
            skipped = skipped,
            sessionId = sessionId,
            isMaterialized = true,
        )

    private fun LocalDate.toVirtualResolved(ruleId: RecurrenceRuleId): ResolvedOccurrence =
        ResolvedOccurrence(
            id = occurrenceIdFor(ruleId, this),
            recurrenceRuleId = ruleId,
            occurrenceDate = this,
            rescheduledToDate = null,
            skipped = false,
            sessionId = null,
            isMaterialized = false,
        )

    private fun occurrenceIdFor(
        ruleId: RecurrenceRuleId,
        date: LocalDate,
    ): RecurrenceOccurrenceId = OccurrenceIdentity.deterministicId(ruleId, date)
}
