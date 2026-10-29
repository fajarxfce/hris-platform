package dev.fajar.hris.administration.domain.repositories

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import java.util.UUID

interface PlatformAuditRepository {
    fun find(id: UUID): Result<PlatformAuditEvent?>

    fun search(query: AuditQuery): Result<Page<PlatformAuditEvent>>
}
