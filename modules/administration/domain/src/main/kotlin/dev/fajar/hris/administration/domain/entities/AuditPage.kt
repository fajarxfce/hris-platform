package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.Collections
import java.util.UUID

class AuditPage(
    val companyId: UUID,
    val from: Instant,
    val until: Instant,
    val evaluatedAt: Instant,
    items: List<AuditEvent>,
    val nextCursor: String?,
) {
    val items: List<AuditEvent> = Collections.unmodifiableList(items.toList())
}
