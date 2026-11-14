package com.stepwise.domain.usecase

import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.MetricId
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.UserId
import kotlinx.datetime.LocalDate

/**
 * What is being completed (`/ARCHITECTURE.md` §19.3's `CompleteOccurrenceUseCase`) — the one
 * split DEV-009 actually needs, between a target with a recurrence behind it (whose Session
 * identity derives from the occurrence, `/core/model/.../SessionIdentity.kt`) and one without
 * (whose identity derives from the source + date directly). Resolving WHICH dates a
 * [RecurrenceRule][com.stepwise.core.model.RecurrenceRule] implies is DEV-010's job (Recurrence
 * Engine) — by the time this reaches [CompleteOccurrenceUseCase], the caller has already decided
 * which occurrence/date it means.
 */
sealed class CompletionTarget {
    abstract val sourceType: ExecutableKind
    abstract val sourceId: String
    abstract val date: LocalDate

    data class OneOff(
        override val sourceType: ExecutableKind,
        override val sourceId: String,
        override val date: LocalDate,
    ) : CompletionTarget()

    data class Recurring(
        override val sourceType: ExecutableKind,
        override val sourceId: String,
        override val date: LocalDate,
        val recurrenceRuleId: RecurrenceRuleId,
    ) : CompletionTarget()
}

/**
 * One metric reading to record against the Session being written. [actualValue] is nullable so
 * [CompleteOccurrenceUseCase] can apply the "Done defaults to planned, Partial requires an
 * explicit actual" rule itself rather than every call site re-implementing it
 * (`/docs/dev-plan-detailed.md`'s DEV-009 checklist: "Done=Planned default; Partial=Actual").
 */
data class MetricEntry(
    val metricId: MetricId,
    val plannedValue: Double?,
    val actualValue: Double?,
)

data class CompleteOccurrenceCommand(
    val userId: UserId,
    val target: CompletionTarget,
    val status: ExecutionStatus,
    val metricValues: List<MetricEntry> = emptyList(),
    val notes: String? = null,
    val rescheduledToDate: LocalDate? = null,
)
