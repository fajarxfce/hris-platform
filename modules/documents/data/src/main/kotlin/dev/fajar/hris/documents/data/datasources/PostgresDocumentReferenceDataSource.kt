package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.schema.Tables.DOCUMENT_EVIDENCE_REFERENCES
import dev.fajar.hris.schema.tables.records.DocumentEvidenceReferencesRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresDocumentReferenceDataSource(private val sql: DSLContext) :
    DocumentReferenceDataSource {
    override fun insert(rows: List<DocumentEvidenceReferencesRecord>) {
        require(rows.size <= 100)
        if (rows.isNotEmpty()) sql.batchInsert(rows).execute()
    }

    override fun referenced(companyId: UUID, revisionIds: Set<UUID>): List<UUID> {
        require(revisionIds.size <= 100)
        if (revisionIds.isEmpty()) return emptyList()
        val r = DOCUMENT_EVIDENCE_REFERENCES
        return sql.selectDistinct(r.DOCUMENT_REVISION_ID)
            .from(r)
            .where(r.COMPANY_ID.eq(companyId), r.DOCUMENT_REVISION_ID.`in`(revisionIds))
            .limit(100)
            .fetch(r.DOCUMENT_REVISION_ID)
    }
}
