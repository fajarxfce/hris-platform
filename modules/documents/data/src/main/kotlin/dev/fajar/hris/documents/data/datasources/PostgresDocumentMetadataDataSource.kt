package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.DocumentMetadataRevisionsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresDocumentMetadataDataSource(private val sql: DSLContext) : DocumentMetadataDataSource {
    override fun history(companyId: UUID, documentId: UUID, after: Long?, limit: Int) =
        sql.selectFrom(DOCUMENT_METADATA_REVISIONS)
            .where(
                DOCUMENT_METADATA_REVISIONS.COMPANY_ID.eq(companyId),
                DOCUMENT_METADATA_REVISIONS.DOCUMENT_ID.eq(documentId),
                after?.let { DOCUMENT_METADATA_REVISIONS.VERSION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(DOCUMENT_METADATA_REVISIONS.VERSION)
            .limit(limit)
            .fetch()

    override fun update(expectedVersion: Long, row: DocumentMetadataRevisionsRecord): Boolean =
        sql.update(DOCUMENTS)
            .set(DOCUMENTS.VERSION, row.documentVersion)
            .set(DOCUMENTS.METADATA_VERSION, row.version)
            .set(DOCUMENTS.CATEGORY, row.category)
            .set(DOCUMENTS.IDENTITY_NUMBER, row.identityNumber)
            .set(DOCUMENTS.ISSUING_COUNTRY, row.issuingCountry)
            .set(DOCUMENTS.ISSUED_ON, row.issuedOn)
            .set(DOCUMENTS.VALID_UNTIL, row.validUntil)
            .where(
                DOCUMENTS.COMPANY_ID.eq(row.companyId),
                DOCUMENTS.ID.eq(row.documentId),
                DOCUMENTS.VERSION.eq(expectedVersion),
                DOCUMENTS.METADATA_VERSION.eq(row.version - 1),
            )
            .execute() == 1

    override fun insert(row: DocumentMetadataRevisionsRecord) {
        sql.insertInto(DOCUMENT_METADATA_REVISIONS).set(row).execute()
    }
}
