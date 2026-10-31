package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

interface MatrixRoomMessageDataSource {
    fun sendRoomMessage(
        roomId: String,
        transactionId: String,
        content: Map<String, Any>,
        token: String,
        deadline: Instant,
    ): JsonNode
}
