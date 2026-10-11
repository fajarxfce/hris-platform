package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.UUID

interface AudienceReferenceRepository {
    fun list(
        companyId: UUID,
        search: AudienceReferenceSearch,
        activeOnly: Boolean,
    ): Result<Page<AudienceReference>>
}
