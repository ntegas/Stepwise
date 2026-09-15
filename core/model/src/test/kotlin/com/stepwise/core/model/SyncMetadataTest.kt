package com.stepwise.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncMetadataTest {
    private val now = Instant.fromEpochMilliseconds(0)

    @Test
    fun `isDeleted is false when deletedAt is null`() {
        val sync = SyncMetadata(updatedAt = now, version = 1, deletedAt = null)

        assertFalse(sync.isDeleted)
    }

    @Test
    fun `isDeleted is true when deletedAt is set`() {
        val sync = SyncMetadata(updatedAt = now, version = 1, deletedAt = now)

        assertTrue(sync.isDeleted)
    }
}
