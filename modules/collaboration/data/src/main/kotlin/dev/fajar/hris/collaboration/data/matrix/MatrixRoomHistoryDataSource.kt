package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

interface MatrixRoomHistoryDataSource {
    fun messages(
        roomId: String,
        from: String?,
        to: String?,
        direction: String,
        limit: Int,
        token: String,
        deadline: Instant,
    ): JsonNode

    fun context(
        roomId: String,
        eventId: String,
        limit: Int,
        token: String,
        deadline: Instant,
    ): JsonNode

    fun event(roomId: String, eventId: String, token: String, deadline: Instant): JsonNode
}
