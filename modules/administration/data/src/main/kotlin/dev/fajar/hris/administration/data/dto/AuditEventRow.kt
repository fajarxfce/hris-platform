package dev.fajar.hris.administration.data.dto

import java.time.OffsetDateTime
import java.util.UUID

data class AuditEventRow(
    val id: UUID,
    val companyId: UUID,
    val actorId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val action: String,
    val correlationId: UUID,
    val recordedAt: OffsetDateTime,
)
