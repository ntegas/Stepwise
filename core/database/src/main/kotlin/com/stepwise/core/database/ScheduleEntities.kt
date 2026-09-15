package com.stepwise.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrenceOccurrence
import com.stepwise.core.model.RecurrenceOccurrenceId
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.RecurrenceRule
import com.stepwise.core.model.RecurrenceRuleId
import com.stepwise.core.model.SessionId
import com.stepwise.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Entity(tableName = "schedules", indices = [Index("userId"), Index(value = ["ownerType", "ownerId"])])
data class RecurrenceRuleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val ownerType: ExecutableKind,
    val ownerId: String,
    val pattern: RecurrencePattern,
    val time: LocalTime?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    @Embedded val sync: SyncColumns,
)

fun RecurrenceRuleEntity.toDomain(): RecurrenceRule =
    RecurrenceRule(
        id = RecurrenceRuleId(id),
        userId = UserId(userId),
        ownerType = ownerType,
        ownerId = ownerId,
        pattern = pattern,
        time = time,
        startsOn = startsOn,
        endsOn = endsOn,
        sync = sync.toDomain(),
    )

fun RecurrenceRule.toEntity(): RecurrenceRuleEntity =
    RecurrenceRuleEntity(
        id = id.value,
        userId = userId.value,
        ownerType = ownerType,
        ownerId = ownerId,
        pattern = pattern,
        time = time,
        startsOn = startsOn,
        endsOn = endsOn,
        sync = sync.toColumns(),
    )

@Dao
interface RecurrenceRuleDao {
    @Upsert
    suspend fun upsert(rule: RecurrenceRuleEntity)

    @Query("SELECT * FROM schedules WHERE ownerType = :ownerType AND ownerId = :ownerId AND deletedAt IS NULL")
    fun observeForOwner(
        ownerType: ExecutableKind,
        ownerId: String,
    ): Flow<List<RecurrenceRuleEntity>>

    @Query("SELECT * FROM schedules WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAll(userId: String): Flow<List<RecurrenceRuleEntity>>
}

// Only materialized occurrences get a row (`/ARCHITECTURE.md` §13) — the common case
// (an unmodified virtual occurrence) is computed on read by DEV-010's occurrence
// resolution, never stored here.
@Entity(tableName = "recurrence_occurrences", indices = [Index("recurrenceRuleId")])
data class RecurrenceOccurrenceEntity(
    @PrimaryKey val id: String,
    val recurrenceRuleId: String,
    val occurrenceDate: LocalDate,
    val rescheduledToDate: LocalDate?,
    val skipped: Boolean,
    val sessionId: String?,
    @Embedded val sync: SyncColumns,
)

fun RecurrenceOccurrenceEntity.toDomain(): RecurrenceOccurrence =
    RecurrenceOccurrence(
        id = RecurrenceOccurrenceId(id),
        recurrenceRuleId = RecurrenceRuleId(recurrenceRuleId),
        occurrenceDate = occurrenceDate,
        rescheduledToDate = rescheduledToDate,
        skipped = skipped,
        sessionId = sessionId?.let(::SessionId),
        sync = sync.toDomain(),
    )

fun RecurrenceOccurrence.toEntity(): RecurrenceOccurrenceEntity =
    RecurrenceOccurrenceEntity(
        id = id.value,
        recurrenceRuleId = recurrenceRuleId.value,
        occurrenceDate = occurrenceDate,
        rescheduledToDate = rescheduledToDate,
        skipped = skipped,
        sessionId = sessionId?.value,
        sync = sync.toColumns(),
    )

@Dao
interface RecurrenceOccurrenceDao {
    @Upsert
    suspend fun upsert(occurrence: RecurrenceOccurrenceEntity)

    @Query("SELECT * FROM recurrence_occurrences WHERE recurrenceRuleId = :ruleId AND deletedAt IS NULL")
    fun observeForRule(ruleId: String): Flow<List<RecurrenceOccurrenceEntity>>

    @Query("SELECT * FROM recurrence_occurrences WHERE id = :id")
    suspend fun getById(id: String): RecurrenceOccurrenceEntity?
}
