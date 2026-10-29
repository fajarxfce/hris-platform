package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

/** Navigation metadata only. Reasons and change payloads have separate disclosure requirements. */
data class AuditEvent(
    override val id: UUID,
    val companyId: UUID,
    override val actorId: UUID,
    override val resourceType: String,
    override val resourceId: UUID,
    override val action: String,
    override val correlationId: UUID,
    override val recordedAt: Instant,
) : AuditMetadata
