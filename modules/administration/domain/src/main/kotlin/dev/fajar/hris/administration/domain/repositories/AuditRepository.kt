package dev.fajar.hris.administration.domain.repositories

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface AuditRepository {
    fun find(companyId: UUID, id: UUID): Result<AuditEvent?>

    fun search(companyId: UUID, query: AuditQuery): Result<Page<AuditEvent>>
}
