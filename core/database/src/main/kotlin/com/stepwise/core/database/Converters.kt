package com.stepwise.core.database

import androidx.room.TypeConverter
import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.ExecutionStatus
import com.stepwise.core.model.GoalProgressMode
import com.stepwise.core.model.GoalType
import com.stepwise.core.model.LifecycleStatus
import com.stepwise.core.model.ProgressSourceKind
import com.stepwise.core.model.RecurrencePattern
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * Room only binds a fixed set of primitive column types — every `:core:model` type an
 * entity stores that isn't already one of those needs a converter here, in one place,
 * rather than each entity inventing its own encoding (`DEVELOPMENT_PROTOCOL.md`'s
 * centralization principle).
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Int? = value?.toEpochDays()

    @TypeConverter
    fun epochDayToLocalDate(value: Int?): LocalDate? = value?.let { LocalDate.fromEpochDays(it) }

    @TypeConverter
    fun localTimeToSecondOfDay(value: LocalTime?): Int? =
        value?.let { it.hour * SECONDS_PER_HOUR + it.minute * SECONDS_PER_MINUTE + it.second }

    @TypeConverter
    fun secondOfDayToLocalTime(value: Int?): LocalTime? =
        value?.let {
            LocalTime(
                hour = it / SECONDS_PER_HOUR,
                minute = (it % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE,
                second = it % SECONDS_PER_MINUTE,
            )
        }

    @TypeConverter
    fun goalTypeToString(value: GoalType?): String? = value?.name

    @TypeConverter
    fun stringToGoalType(value: String?): GoalType? = value?.let { GoalType.valueOf(it) }

    @TypeConverter
    fun goalProgressModeToString(value: GoalProgressMode?): String? = value?.name

    @TypeConverter
    fun stringToGoalProgressMode(value: String?): GoalProgressMode? = value?.let { GoalProgressMode.valueOf(it) }

    @TypeConverter
    fun lifecycleStatusToString(value: LifecycleStatus?): String? = value?.name

    @TypeConverter
    fun stringToLifecycleStatus(value: String?): LifecycleStatus? = value?.let { LifecycleStatus.valueOf(it) }

    @TypeConverter
    fun executionStatusToString(value: ExecutionStatus?): String? = value?.name

    @TypeConverter
    fun stringToExecutionStatus(value: String?): ExecutionStatus? = value?.let { ExecutionStatus.valueOf(it) }

    @TypeConverter
    fun executableKindToString(value: ExecutableKind?): String? = value?.name

    @TypeConverter
    fun stringToExecutableKind(value: String?): ExecutableKind? = value?.let { ExecutableKind.valueOf(it) }

    @TypeConverter
    fun progressSourceKindToString(value: ProgressSourceKind?): String? = value?.name

    @TypeConverter
    fun stringToProgressSourceKind(value: String?): ProgressSourceKind? = value?.let { ProgressSourceKind.valueOf(it) }

    @TypeConverter
    fun recurrencePatternToString(value: RecurrencePattern?): String? = value?.let(::encodeRecurrencePattern)

    @TypeConverter
    fun stringToRecurrencePattern(value: String?): RecurrencePattern? = value?.let(::decodeRecurrencePattern)

    private companion object {
        const val SECONDS_PER_HOUR = 3600
        const val SECONDS_PER_MINUTE = 60
    }
}

// Free functions (not private-in-class) so RecurrencePatternTest can exercise the
// encoding directly without going through a Room-backed Converters instance.
internal fun encodeRecurrencePattern(pattern: RecurrencePattern): String =
    when (pattern) {
        is RecurrencePattern.Daily -> "DAILY"
        is RecurrencePattern.Weekly -> "WEEKLY:" + pattern.daysOfWeek.joinToString(",") { it.name }
        is RecurrencePattern.EveryNDays -> "EVERY_N_DAYS:${pattern.intervalDays}"
        is RecurrencePattern.Monthly -> "MONTHLY:${pattern.dayOfMonth}"
        is RecurrencePattern.Custom -> "CUSTOM:${pattern.rrule}"
    }

internal fun decodeRecurrencePattern(encoded: String): RecurrencePattern {
    val (kind, rest) = encoded.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
    return when (kind) {
        "DAILY" -> RecurrencePattern.Daily
        "WEEKLY" ->
            RecurrencePattern.Weekly(
                requireNotNull(rest) { "WEEKLY pattern missing day list: $encoded" }
                    .split(",")
                    .map { kotlinx.datetime.DayOfWeek.valueOf(it) }
                    .toSet(),
            )
        "EVERY_N_DAYS" -> RecurrencePattern.EveryNDays(requireNotNull(rest).toInt())
        "MONTHLY" -> RecurrencePattern.Monthly(requireNotNull(rest).toInt())
        "CUSTOM" -> RecurrencePattern.Custom(requireNotNull(rest))
        else -> error("Unknown RecurrencePattern encoding: $encoded")
    }
}
