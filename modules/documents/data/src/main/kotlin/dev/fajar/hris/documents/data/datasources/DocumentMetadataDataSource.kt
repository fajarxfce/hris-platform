package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.schema.tables.records.DocumentMetadataRevisionsRecord
import java.util.UUID

interface DocumentMetadataDataSource {
    fun history(
        companyId: UUID,
        documentId: UUID,
        after: Long?,
        limit: Int,
    ): List<DocumentMetadataRevisionsRecord>

    fun update(expectedVersion: Long, row: DocumentMetadataRevisionsRecord): Boolean

    fun insert(row: DocumentMetadataRevisionsRecord)
}
