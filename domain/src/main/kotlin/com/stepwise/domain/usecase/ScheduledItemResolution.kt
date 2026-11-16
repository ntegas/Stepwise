package com.stepwise.domain.usecase

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.Session
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.TodayItem
import com.stepwise.core.model.TodayItemKind
import com.stepwise.core.model.UserId
import kotlinx.datetime.LocalDate

/**
 * Resolves every owner's [RecurrenceRule]-driven occurrences within [range] into [TodayItem]
 * rows - extracted out of [GetTodayAgendaUseCase]'s own private `resolveRecurringItems` once
 * DEV-017 (`/ROADMAP.md`) needed the identical logic over an arbitrary range rather than a
 * single day, per `/ARCHITECTURE.md` §13's "Today and Plan/Calendar call the same
 * occurrence-resolution function" - [GetTodayAgendaUseCase] now calls this with `today..today`
 * (behavior unchanged, verified against its own existing test suite) and
 * [GetPlanRangeUseCase] calls it with the queried range. This is specifically the part of either
 * Use Case's own logic DEV-010/DEV-014 review already found two real bugs in (a duplicate-row
 * collision on a rescheduled-into-an-ordinary-day occurrence; identity/dedup vs. display-date
 * confusion) - sharing it means a future fix to either bug class only has one place to apply.
 *
 * **Filters to [kotlinx.datetime.LocalDate] display dates inside [range], not occurrence
 * identity dates** - [ResolveOccurrencesUseCase.execute] itself includes a materialized
 * occurrence whenever *either* its `occurrenceDate` or its `displayDate` falls in the queried
 * range (its own KDoc explains why: a reschedule must not vanish from either boundary it
 * crosses), so this caller still has to filter down to "appears here," the same way
 * [GetTodayAgendaUseCase] always did.
 */
internal suspend fun <T> resolveScheduledItems(
    userId: UserId,
    range: ClosedRange<LocalDate>,
    kind: TodayItemKind,
    sourceType: ExecutableKind,
    ownersById: Map<String, T>,
    rulesByOwner: Map<Pair<ExecutableKind, String>, List<RecurrenceRule>>,
    sessionsById: Map<SessionId, Session>,
    resolveOccurrencesUseCase: ResolveOccurrencesUseCase,
    describe: (T) -> Pair<String, Int?>,
): AppResult<List<TodayItem>> {
    val items = mutableListOf<TodayItem>()
    for ((ownerId, owner) in ownersById) {
        val rulesForOwner = rulesByOwner[sourceType to ownerId] ?: continue
        for (rule in rulesForOwner) {
            val resolved = resolveOccurrencesUseCase.execute(userId, rule.id, range)
            val occurrences =
                when (resolved) {
                    is AppResult.Failure -> return resolved
                    is AppResult.Success -> resolved.value
                }
            val (title, impactScore) = describe(owner)
            occurrences.filter { it.displayDate in range }.forEach { occurrence ->
                val sessionId = occurrence.sessionId
                val completed = occurrence.skipped || (sessionId != null && sessionsById.containsKey(sessionId))
                items +=
                    TodayItem(
                        kind = kind,
                        sourceType = sourceType,
                        sourceId = ownerId,
                        title = title,
                        date = occurrence.displayDate,
                        time = rule.time,
                        isCompleted = completed,
                        goalImpactScore = impactScore,
                        recurrenceRuleId = rule.id,
                        occurrenceDate = occurrence.occurrenceDate,
                    )
            }
        }
    }
    return AppResult.Success(items)
}
