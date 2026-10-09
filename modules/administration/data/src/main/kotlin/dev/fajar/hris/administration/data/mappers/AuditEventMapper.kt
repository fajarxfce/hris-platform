package dev.fajar.hris.administration.data.mappers

import dev.fajar.hris.administration.data.dto.AuditEventRow
import dev.fajar.hris.administration.domain.entities.AuditEvent
import java.util.UUID

fun AuditEventRow.toAuditEvent(company: UUID): AuditEvent {
    check(companyId == company)
    return AuditEvent(
        id,
        companyId,
        actorId,
        resourceType,
        resourceId,
        action,
        correlationId,
        recordedAt.toInstant(),
    )
}
