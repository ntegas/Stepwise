package com.stepwise.domain.repository

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow

interface RecurrenceRuleRepository {
    fun observeAll(userId: UserId): Flow<List<RecurrenceRule>>

    fun observeForOwner(
        ownerType: ExecutableKind,
        ownerId: String,
    ): Flow<List<RecurrenceRule>>

    // Added at DEV-009 - see RecurrenceRuleDao.getById's own comment.
    suspend fun getById(id: RecurrenceRuleId): AppResult<RecurrenceRule?>

    suspend fun upsert(rule: RecurrenceRule): AppResult<Unit>
}

// Only materialized occurrences get a row (`/ARCHITECTURE.md` §13) — the common case
// (an unmodified virtual occurrence) is computed on read by DEV-010's occurrence
// resolution, never stored or exposed through this repository.
interface RecurrenceOccurrenceRepository {
    fun observeForRule(ruleId: RecurrenceRuleId): Flow<List<RecurrenceOccurrence>>

    suspend fun getById(id: RecurrenceOccurrenceId): AppResult<RecurrenceOccurrence?>

    /**
     * Added at DEV-010 — [com.stepwise.domain.usecase.ResolveOccurrencesUseCase] needs a
     * one-shot snapshot of every materialized row for a rule to merge against the generated
     * virtual dates, not a continuous [Flow] (this is a single resolve-and-return computation,
     * not something a UI observes directly). Lazy materialization (`/ARCHITECTURE.md` §13) keeps
     * per-rule row counts small in practice — loading them all and filtering to a date range
     * in-memory, rather than adding a range-filtered query, is a deliberate simplicity choice,
     * not an oversight; worth revisiting if a rule's materialized-row count ever grows large
     * enough for this to matter.
     */
    suspend fun getAllForRule(ruleId: RecurrenceRuleId): AppResult<List<RecurrenceOccurrence>>

    suspend fun upsert(occurrence: RecurrenceOccurrence): AppResult<Unit>

    /**
     * Reads the current row for [id] (if any) and writes whatever [mutate] decides, both inside
     * one local transaction. Added at DEV-010 review (adversarial/security/logic reviewers,
     * independently converging on the same gap) to close the TOCTOU race between
     * [com.stepwise.domain.usecase.SkipOccurrenceUseCase] and
     * [com.stepwise.domain.usecase.CompleteOccurrenceUseCase]'s otherwise-independent
     * read-then-upsert pairs against the same occurrence row — without this, Skip's own "can't
     * skip a completed occurrence" guard could be raced around by a concurrent completion
     * landing between Skip's read and its write (and vice versa). [mutate] runs inside the
     * transaction and can itself fail (e.g. Skip's guard) without writing anything; nothing else
     * can observe or write this row while [mutate] is deciding, since SQLite serializes writers
     * against one write connection.
     */
    suspend fun upsertAtomically(
        id: RecurrenceOccurrenceId,
        mutate: (existing: RecurrenceOccurrence?) -> AppResult<RecurrenceOccurrence>,
    ): AppResult<RecurrenceOccurrence>
}
