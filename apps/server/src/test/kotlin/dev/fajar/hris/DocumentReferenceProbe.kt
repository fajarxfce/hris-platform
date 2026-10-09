package dev.fajar.hris

import dev.fajar.hris.documents.data.datasources.DocumentReferenceDataSource
import dev.fajar.hris.schema.Tables.DOCUMENT_EVIDENCE_REFERENCES
import dev.fajar.hris.schema.tables.records.DocumentEvidenceReferencesRecord
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*

/** Lets a test omit a required registration and verify the database rejects the business commit. */
class DocumentReferenceProbe(private val sql: DSLContext) : DocumentReferenceDataSource {
    @Volatile var skipWrites = false

    override fun insert(rows: List<DocumentEvidenceReferencesRecord>) {
        if (!skipWrites && rows.isNotEmpty()) sql.batchInsert(rows).execute()
    }

    override fun referenced(companyId: UUID, revisionIds: Set<UUID>): List<UUID> {
        val r = DOCUMENT_EVIDENCE_REFERENCES
        return sql.selectDistinct(r.DOCUMENT_REVISION_ID)
            .from(r)
            .where(r.COMPANY_ID.eq(companyId), r.DOCUMENT_REVISION_ID.`in`(revisionIds))
            .limit(100)
            .fetch(r.DOCUMENT_REVISION_ID)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class DocumentReferenceProbeConfiguration {
    @Bean @Primary fun documentReferenceProbe(sql: DSLContext) = DocumentReferenceProbe(sql)
}
