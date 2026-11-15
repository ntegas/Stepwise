package com.stepwise.domain.usecase

import com.stepwise.core.model.ExecutableKind
import com.stepwise.core.model.RecurrencePattern
import com.stepwise.core.model.UserId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class CreateRecurrenceRuleCommand(
    val userId: UserId,
    val ownerType: ExecutableKind,
    val ownerId: String,
    val pattern: RecurrencePattern,
    val startsOn: LocalDate,
    val time: LocalTime? = null,
    val endsOn: LocalDate? = null,
)
