package com.stepwise.data.sync

import com.stepwise.core.network.PostgrestSyncClient
import com.stepwise.core.sync.SyncTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The one adapter from `:core:sync`'s [SyncTransport] interface to `:core:network`'s
 * [PostgrestSyncClient] — `:data` is "the ONLY module allowed to depend on both `:core:database`
 * and `:core:network` at once" (`/ARCHITECTURE.md` §5), which is exactly why this file lives
 * here and not in `:core:sync` itself (see `SyncEngine`'s own KDoc for the full reasoning).
 */
class SyncTransportImpl(
    private val client: PostgrestSyncClient,
) : SyncTransport {
    override suspend fun upsert(
        table: String,
        payloadJson: String,
    ) = client.upsert(table, payloadJson.toJsonObject())

    override suspend fun compareAndSwapUpdate(
        table: String,
        id: String,
        expectedVersion: Int,
        payloadJson: String,
    ): Int? = client.compareAndSwapUpdate(table, id, expectedVersion, payloadJson.toJsonObject())

    override suspend fun fetchVersion(
        table: String,
        id: String,
    ): Int? = client.fetchById(table, id)?.get("version")?.jsonPrimitive?.content?.toIntOrNull()

    private fun String.toJsonObject(): JsonObject = Json.parseToJsonElement(this) as JsonObject
}
