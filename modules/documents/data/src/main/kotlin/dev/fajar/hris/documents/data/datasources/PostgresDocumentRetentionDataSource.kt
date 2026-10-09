package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresDocumentRetentionDataSource(private val sql: DSLContext) :
    DocumentRetentionDataSource {
    override fun policy(companyId: UUID, id: UUID): DocumentRetentionPolicyViewsRecord? {
        val p = DOCUMENT_RETENTION_POLICY_VIEWS
        return sql.selectFrom(p)
            .where(p.COMPANY_ID.eq(companyId), p.ID.eq(id), p.VERSION.eq(p.CURRENT_VERSION))
            .fetchOne()
    }

    override fun policyFor(
        companyId: UUID,
        classification: String,
    ): DocumentRetentionPolicyViewsRecord? {
        val p = DOCUMENT_RETENTION_POLICY_VIEWS
        return sql.selectFrom(p)
            .where(
                p.COMPANY_ID.eq(companyId),
                p.CLASSIFICATION.eq(classification),
                p.VERSION.eq(p.CURRENT_VERSION),
            )
            .fetchOne()
    }

    override fun policies(companyId: UUID): List<DocumentRetentionPolicyViewsRecord> {
        val p = DOCUMENT_RETENTION_POLICY_VIEWS
        return sql.selectFrom(p)
            .where(p.COMPANY_ID.eq(companyId), p.VERSION.eq(p.CURRENT_VERSION))
            .orderBy(p.CLASSIFICATION)
            .limit(3)
            .fetch()
    }

    override fun policyHistory(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<DocumentRetentionPolicyViewsRecord> {
        require(limit in 1..201)
        val p = DOCUMENT_RETENTION_POLICY_VIEWS
        return sql.selectFrom(p)
            .where(
                p.COMPANY_ID.eq(companyId),
                p.ID.eq(id),
                after?.let { p.VERSION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(p.VERSION)
            .limit(limit)
            .fetch()
    }

    override fun insertPolicy(row: DocumentRetentionPoliciesRecord) {
        sql.insertInto(DOCUMENT_RETENTION_POLICIES).set(row).execute()
    }

    override fun advancePolicy(companyId: UUID, id: UUID, expectedVersion: Long): Long? {
        val p = DOCUMENT_RETENTION_POLICIES
        return sql.update(p)
            .set(p.VERSION, expectedVersion + 1)
            .where(p.COMPANY_ID.eq(companyId), p.ID.eq(id), p.VERSION.eq(expectedVersion))
            .returning(p.VERSION)
            .fetchOne()
            ?.version
    }

    override fun appendPolicy(row: DocumentRetentionPolicyRevisionsRecord) {
        sql.insertInto(DOCUMENT_RETENTION_POLICY_REVISIONS).set(row).execute()
    }

    override fun state(companyId: UUID, documentId: UUID): DocumentRetentionStateViewsRecord? {
        val s = DOCUMENT_RETENTION_STATE_VIEWS
        return sql.selectFrom(s)
            .where(
                s.COMPANY_ID.eq(companyId),
                s.DOCUMENT_ID.eq(documentId),
                s.VERSION.eq(s.CURRENT_VERSION),
            )
            .fetchOne()
    }

    override fun insertState(row: DocumentRetentionStatesRecord) {
        sql.insertInto(DOCUMENT_RETENTION_STATES).set(row).execute()
    }

    override fun advanceState(companyId: UUID, documentId: UUID, expectedVersion: Long): Long? {
        val s = DOCUMENT_RETENTION_STATES
        return sql.update(s)
            .set(s.VERSION, expectedVersion + 1)
            .where(
                s.COMPANY_ID.eq(companyId),
                s.DOCUMENT_ID.eq(documentId),
                s.VERSION.eq(expectedVersion),
            )
            .returning(s.VERSION)
            .fetchOne()
            ?.version
    }

    override fun appendChange(row: DocumentRetentionChangesRecord) {
        sql.insertInto(DOCUMENT_RETENTION_CHANGES).set(row).execute()
    }

    override fun history(
        companyId: UUID,
        documentId: UUID,
        after: Long?,
        limit: Int,
    ): List<DocumentRetentionStateViewsRecord> {
        require(limit in 1..201)
        val s = DOCUMENT_RETENTION_STATE_VIEWS
        return sql.selectFrom(s)
            .where(
                s.COMPANY_ID.eq(companyId),
                s.DOCUMENT_ID.eq(documentId),
                after?.let { s.VERSION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(s.VERSION)
            .limit(limit)
            .fetch()
    }
}
