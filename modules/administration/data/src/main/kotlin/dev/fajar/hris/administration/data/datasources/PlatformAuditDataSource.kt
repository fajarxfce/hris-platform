package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.administration.data.dto.AuditQueryRow
import dev.fajar.hris.administration.data.dto.PlatformAccountAccessAuditRow
import dev.fajar.hris.administration.data.dto.PlatformAuditEventRow
import java.util.UUID

interface PlatformAuditDataSource {
    fun accountAccess(id: UUID): PlatformAccountAccessAuditRow?

    fun find(id: UUID): PlatformAuditEventRow?

    fun search(query: AuditQueryRow): List<PlatformAuditEventRow>
}
