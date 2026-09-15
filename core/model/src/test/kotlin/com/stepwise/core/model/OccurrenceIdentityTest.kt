package com.stepwise.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OccurrenceIdentityTest {
    private val ruleA = RecurrenceRuleId("rule-a")
    private val ruleB = RecurrenceRuleId("rule-b")
    private val dateOne = LocalDate(2026, 9, 15)
    private val dateTwo = LocalDate(2026, 9, 16)

    @Test
    fun `same rule and date always produce the same id`() {
        val first = OccurrenceIdentity.deterministicId(ruleA, dateOne)
        val second = OccurrenceIdentity.deterministicId(ruleA, dateOne)

        assertEquals(first, second)
    }

    @Test
    fun `different dates for the same rule produce different ids`() {
        val first = OccurrenceIdentity.deterministicId(ruleA, dateOne)
        val second = OccurrenceIdentity.deterministicId(ruleA, dateTwo)

        assertNotEquals(first, second)
    }

    @Test
    fun `different rules for the same date produce different ids`() {
        val first = OccurrenceIdentity.deterministicId(ruleA, dateOne)
        val second = OccurrenceIdentity.deterministicId(ruleB, dateOne)

        assertNotEquals(first, second)
    }

    @Test
    fun `two independently materializing devices converge on the same id`() {
        // Simulates B9's scenario: two devices, no shared state, each computing the ID
        // for "the same" logical occurrence independently.
        val deviceOne = OccurrenceIdentity.deterministicId(ruleA, dateOne)
        val deviceTwo = OccurrenceIdentity.deterministicId(ruleA, dateOne)

        assertEquals(deviceOne, deviceTwo)
    }
}
