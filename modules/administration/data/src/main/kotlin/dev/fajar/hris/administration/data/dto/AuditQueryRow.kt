package dev.fajar.hris.administration.data.dto

import java.time.OffsetDateTime
import java.util.UUID

data class AuditQueryRow(
    val from: OffsetDateTime,
    val until: OffsetDateTime,
    val actorId: UUID?,
    val resourceType: String?,
    val resourceId: UUID?,
    val action: String?,
    val beforeAt: OffsetDateTime?,
    val beforeId: UUID?,
    val limit: Int,
)
