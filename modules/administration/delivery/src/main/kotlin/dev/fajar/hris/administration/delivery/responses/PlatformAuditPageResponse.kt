package dev.fajar.hris.administration.delivery.responses

import java.time.Instant

data class PlatformAuditPageResponse(
    val from: Instant,
    val until: Instant,
    val evaluatedAt: Instant,
    val items: List<PlatformAuditEventResponse>,
    val nextCursor: String?,
)
