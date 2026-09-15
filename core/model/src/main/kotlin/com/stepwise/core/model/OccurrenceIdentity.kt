package com.stepwise.core.model

import kotlinx.datetime.LocalDate

/**
 * Computes a [RecurrenceOccurrenceId]'s stable, deterministic identity (decision B9,
 * `/ARCHITECTURE.md` §13): "a stable hash of `(recurrenceRuleId, occurrenceDate)`, not a
 * randomly generated UUID chosen at materialization time." This is what makes two devices
 * that independently decide "this occurrence needs a row" — one completing it offline, another
 * rescheduling the same date before either has synced — compute the *same* ID and converge on
 * one row through the ordinary upsert-by-UUID sync path, instead of producing two rows for one
 * logical occurrence.
 *
 * Deliberately a plain composite string, not a cryptographic hash: correctness only requires
 * that the same `(ruleId, date)` pair always produce the same output and different pairs never
 * collide, and direct concatenation guarantees both by construction with no dependency on a
 * hash algorithm's stability or collision resistance — simpler, and nothing to get subtly wrong.
 * The result deliberately does *not* look like the stringified-UUID `/DATA_MODEL.md` uses for
 * every other ID: that would suggest a random, freely-chosen value, which is exactly what B9
 * rejects for this one ID.
 */
object OccurrenceIdentity {
    fun deterministicId(
        recurrenceRuleId: RecurrenceRuleId,
        occurrenceDate: LocalDate,
    ): RecurrenceOccurrenceId = RecurrenceOccurrenceId("occurrence:${recurrenceRuleId.value}:$occurrenceDate")
}
