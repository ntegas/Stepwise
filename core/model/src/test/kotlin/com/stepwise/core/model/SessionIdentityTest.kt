package com.stepwise.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val UUID_SHAPE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

class SessionIdentityTest {
    private val occurrenceA = RecurrenceOccurrenceId("occurrence:rule-a:2026-09-15")
    private val occurrenceB = RecurrenceOccurrenceId("occurrence:rule-b:2026-09-15")
    private val dateOne = LocalDate(2026, 9, 15)
    private val dateTwo = LocalDate(2026, 9, 16)

    @Test
    fun `the output is formatted as a real UUID string`() {
        val id = SessionIdentity.deterministicId(occurrenceA)

        assertTrue(UUID_SHAPE.matches(id.value))
    }

    @Test
    fun `same occurrence always produces the same Session id`() {
        val first = SessionIdentity.deterministicId(occurrenceA)
        val second = SessionIdentity.deterministicId(occurrenceA)

        assertEquals(first, second)
    }

    @Test
    fun `different occurrences produce different Session ids`() {
        val first = SessionIdentity.deterministicId(occurrenceA)
        val second = SessionIdentity.deterministicId(occurrenceB)

        assertNotEquals(first, second)
    }

    @Test
    fun `same one-off source and date always produce the same Session id`() {
        val first = SessionIdentity.deterministicId(ExecutableKind.ACTIVITY, "activity-1", dateOne)
        val second = SessionIdentity.deterministicId(ExecutableKind.ACTIVITY, "activity-1", dateOne)

        assertEquals(first, second)
    }

    @Test
    fun `the same one-off source on two different dates produces two different Session ids`() {
        // A real finding from this task's own design review: a one-off id scheme that ignored
        // the date would collapse "ran today" and "ran tomorrow" into one Session.
        val today = SessionIdentity.deterministicId(ExecutableKind.ACTIVITY, "activity-1", dateOne)
        val tomorrow = SessionIdentity.deterministicId(ExecutableKind.ACTIVITY, "activity-1", dateTwo)

        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `different source types for the same id and date produce different Session ids`() {
        val task = SessionIdentity.deterministicId(ExecutableKind.TASK, "shared-id", dateOne)
        val habit = SessionIdentity.deterministicId(ExecutableKind.HABIT, "shared-id", dateOne)

        assertNotEquals(task, habit)
    }

    @Test
    fun `a recurring occurrence's Session id never collides with a one-off Session id`() {
        val recurring = SessionIdentity.deterministicId(occurrenceA)
        val oneOff = SessionIdentity.deterministicId(ExecutableKind.ACTIVITY, occurrenceA.value, dateOne)

        assertNotEquals(recurring, oneOff)
    }

    @Test
    fun `same session and metric always produce the same metric value id`() {
        val sessionId = SessionIdentity.deterministicId(occurrenceA)
        val metricId = MetricId("duration_minutes")

        val first = SessionIdentity.deterministicMetricValueId(sessionId, metricId)
        val second = SessionIdentity.deterministicMetricValueId(sessionId, metricId)

        assertEquals(first, second)
        assertTrue(UUID_SHAPE.matches(first.value))
    }

    @Test
    fun `different metrics on the same session produce different metric value ids`() {
        val sessionId = SessionIdentity.deterministicId(occurrenceA)

        val duration = SessionIdentity.deterministicMetricValueId(sessionId, MetricId("duration_minutes"))
        val distance = SessionIdentity.deterministicMetricValueId(sessionId, MetricId("distance_km"))

        assertNotEquals(duration, distance)
    }
}
