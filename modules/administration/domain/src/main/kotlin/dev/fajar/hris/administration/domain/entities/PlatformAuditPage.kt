package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.Collections

class PlatformAuditPage(
    val from: Instant,
    val until: Instant,
    val evaluatedAt: Instant,
    items: List<PlatformAuditEvent>,
    val nextCursor: String?,
) {
    val items: List<PlatformAuditEvent> = Collections.unmodifiableList(items.toList())
}
