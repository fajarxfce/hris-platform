package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

interface MatrixSyncDataSource {
    /**
     * One owned, non-queued long-poll slot per user/device; close on every result or cancellation.
     */
    fun open(
        userId: String,
        deviceId: String,
        bearer: String,
        deadline: Instant,
    ): MatrixSyncConnection
}

/** A scoped connection handle, not a selectable provider URL or general-purpose proxy. */
interface MatrixSyncConnection : AutoCloseable {
    fun registerFilter(filter: Map<String, Any>): String

    fun sync(since: String?, filterId: String, timeoutMillis: Int, fullState: Boolean): JsonNode

    fun roomState(roomId: String): JsonNode
}
