package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.schema.tables.records.DocumentEvidenceReferencesRecord
import java.util.UUID

interface DocumentReferenceDataSource {
    fun insert(rows: List<DocumentEvidenceReferencesRecord>)

    fun referenced(companyId: UUID, revisionIds: Set<UUID>): List<UUID>
}
