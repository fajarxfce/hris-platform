package dev.fajar.hris.documents.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.documents.domain.entities.DocumentReferenceOrigin
import java.time.Instant
import java.util.UUID

interface DocumentReferenceRepository {
    /** The caller owns the company document guard and the business transaction. */
    fun retain(
        companyId: UUID,
        origin: DocumentReferenceOrigin,
        revisionIds: Set<UUID>,
        recordedBy: UUID,
        at: Instant,
    ): Result<Unit>

    fun referenced(companyId: UUID, revisionIds: Set<UUID>): Result<Set<UUID>>
}
