package dev.fajar.hris.documents.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.DocumentMetadataDataSource
import dev.fajar.hris.documents.data.mappers.*
import dev.fajar.hris.documents.domain.entities.DocumentMetadataRevision
import dev.fajar.hris.documents.domain.repositories.DocumentMetadataRepository
import java.util.UUID

class StoredDocumentMetadataRepository(private val source: DocumentMetadataDataSource) :
    DocumentMetadataRepository {
    override fun history(companyId: UUID, documentId: UUID, after: Long?, limit: Int) =
        safeDatabaseCall {
            require(limit in 1..200)
            val rows = source.history(companyId, documentId, after, limit + 1)
            require(rows.size <= limit + 1)
            Page(
                rows.take(limit).map { it.toMetadataRevision() },
                if (rows.size > limit) rows[limit - 1].version.toString() else null,
            )
        }

    override fun save(
        actor: Actor,
        expectedDocumentVersion: Long,
        revision: DocumentMetadataRevision,
    ): Result<MutationReceipt> =
        safeDatabaseCall { revision.toMetadataRow(requireNotNull(actor.companyId)) }
            .flatMap { row ->
                safeDatabaseCall { source.update(expectedDocumentVersion, row) }
                    .flatMap { changed ->
                        if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                        else
                            safeDatabaseCall {
                                source.insert(row)
                                MutationReceipt(revision.documentId, revision.documentVersion)
                            }
                    }
            }
}
