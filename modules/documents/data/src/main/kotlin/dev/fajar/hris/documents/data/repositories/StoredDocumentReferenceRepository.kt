package dev.fajar.hris.documents.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.documents.data.datasources.DocumentReferenceDataSource
import dev.fajar.hris.documents.domain.entities.DocumentReferenceOrigin
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.schema.tables.records.DocumentEvidenceReferencesRecord
import java.time.*
import java.util.UUID

class StoredDocumentReferenceRepository(private val source: DocumentReferenceDataSource) :
    DocumentReferenceRepository {
    override fun retain(
        companyId: UUID,
        origin: DocumentReferenceOrigin,
        revisionIds: Set<UUID>,
        recordedBy: UUID,
        at: Instant,
    ) = safeDatabaseCall {
        require(revisionIds.size <= 100)
        source.insert(
            revisionIds.sorted().map { id ->
                DocumentEvidenceReferencesRecord().also {
                    it.companyId = companyId
                    it.sourceKind = origin.kind.name
                    it.sourceId = origin.resourceId
                    it.sourceVersion = origin.version
                    it.documentRevisionId = id
                    it.createdBy = recordedBy
                    it.createdAt = at.atOffset(ZoneOffset.UTC)
                }
            }
        )
    }

    override fun referenced(companyId: UUID, revisionIds: Set<UUID>) = safeDatabaseCall {
        source.referenced(companyId, revisionIds).toSet()
    }
}
