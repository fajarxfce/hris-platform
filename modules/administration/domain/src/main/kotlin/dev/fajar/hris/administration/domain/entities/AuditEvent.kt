package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

/** Navigation metadata only. Reasons and change payloads have separate disclosure requirements. */
data class AuditEvent(
    val id: UUID,
    val companyId: UUID,
    val actorId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val action: String,
    val correlationId: UUID,
    val recordedAt: Instant,
)
