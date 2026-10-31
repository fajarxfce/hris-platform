package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

/** Fixed protocol exchanges. Scope, signature validation and publication belong to the use case. */
interface MatrixKeyDataSource {
    fun claimKeys(
        keys: Map<String, Map<String, String>>,
        token: String,
        deadline: Instant,
    ): JsonNode

    fun queryDevices(devices: Map<String, List<String>>, token: String, deadline: Instant): JsonNode

    fun queryDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): JsonNode

    fun uploadKeys(body: Map<String, Any>, token: String, deadline: Instant): JsonNode
}
