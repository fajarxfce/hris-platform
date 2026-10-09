package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.administration.data.dto.AuditEventRow
import dev.fajar.hris.administration.data.dto.AuditQueryRow
import java.util.UUID

interface AuditDataSource {
    fun find(companyId: UUID, id: UUID): AuditEventRow?

    fun search(companyId: UUID, query: AuditQueryRow): List<AuditEventRow>
}
