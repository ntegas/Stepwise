package com.stepwise.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val UUID_PATTERN =
    Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    )

class IdGeneratorTest {
    @Test
    fun `UuidIdGenerator produces well-formed, unique IDs`() {
        val generator: IdGenerator = UuidIdGenerator()

        val first = generator.newId()
        val second = generator.newId()

        assertTrue(UUID_PATTERN.matches(first), "not a UUID: $first")
        assertTrue(UUID_PATTERN.matches(second), "not a UUID: $second")
        assertNotEquals(first, second)
    }

    @Test
    fun `FakeIdGenerator is deterministic and sequential`() {
        val generator = FakeIdGenerator(prefix = "session")

        assertEquals("session-1", generator.newId())
        assertEquals("session-2", generator.newId())
        assertEquals("session-3", generator.newId())
    }
}
