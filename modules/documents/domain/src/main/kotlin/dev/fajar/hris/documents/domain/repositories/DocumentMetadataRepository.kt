package dev.fajar.hris.documents.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentMetadataRevision
import java.util.UUID

interface DocumentMetadataRepository {
    fun history(
        companyId: UUID,
        documentId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<DocumentMetadataRevision>>

    fun save(
        actor: Actor,
        expectedDocumentVersion: Long,
        revision: DocumentMetadataRevision,
    ): Result<MutationReceipt>
}
