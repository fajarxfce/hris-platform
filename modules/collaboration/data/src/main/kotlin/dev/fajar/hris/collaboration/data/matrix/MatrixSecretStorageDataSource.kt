package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

interface MatrixSecretStorageDataSource {
    fun defaultKey(userId: String, token: String, deadline: Instant): JsonNode?
}
