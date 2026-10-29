package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.administration.data.dto.AuditQueryRow
import dev.fajar.hris.administration.data.dto.PlatformAuditEventRow
import java.util.UUID

interface PlatformAuditDataSource {
    fun find(id: UUID): PlatformAuditEventRow?

    fun search(query: AuditQueryRow): List<PlatformAuditEventRow>
}
