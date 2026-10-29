package dev.fajar.hris.administration.delivery.responses

import java.time.Instant
import java.util.UUID

data class PlatformAuditEventResponse(
    val id: UUID,
    val actorId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val action: String,
    val correlationId: UUID,
    val recordedAt: Instant,
)
