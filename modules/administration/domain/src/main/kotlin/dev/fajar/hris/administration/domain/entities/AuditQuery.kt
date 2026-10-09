package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

data class AuditQuery(
    val from: Instant,
    val until: Instant,
    val actorId: UUID?,
    val resourceType: String?,
    val resourceId: UUID?,
    val action: String?,
    val limit: Int,
    val before: AuditEvent? = null,
)
