package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

interface MatrixRoomAccessDataSource {
    fun join(roomId: String, token: String, deadline: Instant): JsonNode

    fun receipt(
        roomId: String,
        receiptType: String,
        eventId: String,
        token: String,
        deadline: Instant,
    ): JsonNode

    fun stateEvents(roomId: String, token: String, deadline: Instant): JsonNode

    fun memberEvents(roomId: String, at: String?, token: String, deadline: Instant): JsonNode
}
