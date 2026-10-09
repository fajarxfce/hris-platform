package dev.fajar.hris.administration.delivery.responses

import java.time.Instant
import java.util.UUID

data class AuditPageResponse(
    val companyId: UUID,
    val from: Instant,
    val until: Instant,
    val evaluatedAt: Instant,
    val items: List<AuditEventResponse>,
    val nextCursor: String?,
)
