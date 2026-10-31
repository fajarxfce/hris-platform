package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

/** Fixed protocol exchanges. Scope, signature validation and publication belong to the use case. */
interface MatrixKeyDataSource {
    fun queryDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): JsonNode

    fun uploadKeys(body: Map<String, Any>, token: String, deadline: Instant): JsonNode
}
