package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

interface MatrixToDeviceDataSource {
    fun sendEncrypted(
        transactionId: String,
        body: Map<String, Any>,
        token: String,
        deadline: Instant,
    ): JsonNode
}
