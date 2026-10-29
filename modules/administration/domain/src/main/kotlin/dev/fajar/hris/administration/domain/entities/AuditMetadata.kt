package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

/** Shared navigation fields; scope remains explicit in each concrete projection. */
interface AuditMetadata {
    val id: UUID
    val actorId: UUID
    val resourceType: String
    val resourceId: UUID
    val action: String
    val correlationId: UUID
    val recordedAt: Instant
}
