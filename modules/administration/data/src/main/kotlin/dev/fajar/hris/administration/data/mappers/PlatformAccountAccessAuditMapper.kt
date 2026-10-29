package dev.fajar.hris.administration.data.mappers

import dev.fajar.hris.administration.data.dto.PlatformAccountAccessAuditRow
import dev.fajar.hris.administration.domain.entities.PlatformAccountAccessAudit
import java.util.UUID

fun PlatformAccountAccessAuditRow.toAccountAccessAudit(
    expectedId: UUID
): PlatformAccountAccessAudit {
    check(id == expectedId)
    check(permissions.size <= 32 && permissions.distinct().size == permissions.size)
    check(permissions.all { Regex("[a-z][a-z0-9_.:-]{0,79}").matches(it) })
    check(reason == null || reason.codePointCount(0, reason.length) <= 1000)
    return PlatformAccountAccessAudit(id, accountId, active, permissions, reason)
}
