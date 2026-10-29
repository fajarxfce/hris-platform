package dev.fajar.hris.administration.data.mappers

import dev.fajar.hris.administration.data.dto.PlatformAuditEventRow
import dev.fajar.hris.administration.domain.entities.PlatformAuditEvent

fun PlatformAuditEventRow.toPlatformAuditEvent(): PlatformAuditEvent {
    return PlatformAuditEvent(
        id,
        actorId,
        resourceType,
        resourceId,
        action,
        correlationId,
        recordedAt.toInstant(),
    )
}
