package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

data class AuditSearch(
    val from: Instant? = null,
    val until: Instant? = null,
    val cursor: UUID? = null,
    val actorId: UUID? = null,
    val resourceType: String? = null,
    val resourceId: UUID? = null,
    val action: String? = null,
    val limit: Int = 50,
)
