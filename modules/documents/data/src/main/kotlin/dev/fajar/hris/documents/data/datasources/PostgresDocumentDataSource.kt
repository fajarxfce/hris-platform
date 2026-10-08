package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.models.DocumentCapacityData
import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresDocumentDataSource(private val sql: DSLContext) : DocumentDataSource {
    override fun lock(companyId: UUID) {
        sql.execute("select pg_advisory_xact_lock(hashtextextended(?::text,7319))", companyId)
    }

    override fun capacity(companyId: UUID, actorId: UUID): DocumentCapacityData {
        val row =
            sql.fetchOne(
                """
                select (select count(*) from documents where company_id=?) as documents,
                    count(*) as active,count(*) filter(where created_by=?) as own,
                    coalesce(sum(expected_bytes-uploaded_bytes-coalesce((select sum(byte_count) from document_upload_chunks c
                        where c.company_id=r.company_id and c.revision_id=r.id and c.status='PENDING' and c.current_attempt_id is not null),0)),0) as unfilled
                from document_revisions r where company_id=? and status='UPLOADING' and expires_at>clock_timestamp()
                """
                    .trimIndent(),
                companyId,
                actorId,
                companyId,
            )!!
        return DocumentCapacityData(
            row.get("documents", Long::class.java),
            row.get("active", Long::class.java),
            row.get("own", Long::class.java),
            row.get("unfilled", Long::class.java),
        )
    }

    override fun find(companyId: UUID, id: UUID) =
        sql.selectFrom(DOCUMENTS)
            .where(DOCUMENTS.COMPANY_ID.eq(companyId), DOCUMENTS.ID.eq(id))
            .fetchOne()

    override fun revision(companyId: UUID, id: UUID) =
        sql.selectFrom(DOCUMENT_REVISIONS)
            .where(DOCUMENT_REVISIONS.COMPANY_ID.eq(companyId), DOCUMENT_REVISIONS.ID.eq(id))
            .fetchOne()

    override fun activeRevision(companyId: UUID, documentId: UUID) =
        sql.selectFrom(DOCUMENT_REVISIONS)
            .where(
                DOCUMENT_REVISIONS.COMPANY_ID.eq(companyId),
                DOCUMENT_REVISIONS.DOCUMENT_ID.eq(documentId),
                DOCUMENT_REVISIONS.STATUS.eq("UPLOADING"),
            )
            .fetchOne()

    override fun list(
        companyId: UUID,
        employmentId: UUID,
        classifications: Set<String>,
        after: UUID?,
        limit: Int,
    ) =
        sql.selectFrom(DOCUMENTS)
            .where(
                DOCUMENTS.COMPANY_ID.eq(companyId),
                DOCUMENTS.EMPLOYMENT_ID.eq(employmentId),
                DOCUMENTS.CLASSIFICATION.`in`(classifications),
                after?.let { DOCUMENTS.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(DOCUMENTS.ID)
            .limit(limit)
            .fetch()

    override fun revisions(companyId: UUID, documentId: UUID, after: Int?, limit: Int) =
        sql.selectFrom(DOCUMENT_REVISIONS)
            .where(
                DOCUMENT_REVISIONS.COMPANY_ID.eq(companyId),
                DOCUMENT_REVISIONS.DOCUMENT_ID.eq(documentId),
                after?.let { DOCUMENT_REVISIONS.REVISION_NO.lt(it) } ?: DSL.noCondition(),
            )
            .orderBy(DOCUMENT_REVISIONS.REVISION_NO.desc())
            .limit(limit)
            .fetch()

    override fun insertDocument(row: DocumentsRecord) {
        sql.insertInto(DOCUMENTS).set(row).execute()
    }

    override fun incrementRevision(companyId: UUID, id: UUID, version: Long) =
        sql.update(DOCUMENTS)
            .set(DOCUMENTS.VERSION, version + 1)
            .set(DOCUMENTS.REVISION_COUNT, DOCUMENTS.REVISION_COUNT.plus(1))
            .where(
                DOCUMENTS.COMPANY_ID.eq(companyId),
                DOCUMENTS.ID.eq(id),
                DOCUMENTS.VERSION.eq(version),
            )
            .returning()
            .fetchOne()

    override fun insertRevision(row: DocumentRevisionsRecord) {
        sql.insertInto(DOCUMENT_REVISIONS).set(row).execute()
    }

    override fun transition(companyId: UUID, id: UUID, version: Long, status: String) =
        sql.update(DOCUMENT_REVISIONS)
            .set(DOCUMENT_REVISIONS.STATUS, status)
            .set(DOCUMENT_REVISIONS.VERSION, version + 1)
            .where(
                DOCUMENT_REVISIONS.COMPANY_ID.eq(companyId),
                DOCUMENT_REVISIONS.ID.eq(id),
                DOCUMENT_REVISIONS.VERSION.eq(version),
                DOCUMENT_REVISIONS.STATUS.eq("UPLOADING"),
            )
            .returning()
            .fetchOne()

    override fun chunk(companyId: UUID, revisionId: UUID, id: UUID) =
        sql.selectFrom(DOCUMENT_UPLOAD_CHUNKS)
            .where(
                DOCUMENT_UPLOAD_CHUNKS.COMPANY_ID.eq(companyId),
                DOCUMENT_UPLOAD_CHUNKS.REVISION_ID.eq(revisionId),
                DOCUMENT_UPLOAD_CHUNKS.ID.eq(id),
            )
            .fetchOne()

    override fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int) =
        sql.selectFrom(DOCUMENT_UPLOAD_CHUNKS)
            .where(
                DOCUMENT_UPLOAD_CHUNKS.COMPANY_ID.eq(companyId),
                DOCUMENT_UPLOAD_CHUNKS.REVISION_ID.eq(revisionId),
                DOCUMENT_UPLOAD_CHUNKS.ORDINAL.eq(ordinal),
            )
            .fetchOne()

    override fun insertChunk(row: DocumentUploadChunksRecord) {
        sql.insertInto(DOCUMENT_UPLOAD_CHUNKS).set(row).execute()
    }

    override fun insertAttempt(row: DocumentUploadAttemptsRecord) {
        sql.insertInto(DOCUMENT_UPLOAD_ATTEMPTS).set(row).execute()
    }

    override fun reserveAttempt(
        companyId: UUID,
        revisionId: UUID,
        id: UUID,
        attempts: Int,
        attemptId: UUID,
        key: String,
        seconds: Int,
    ) =
        sql.update(DOCUMENT_UPLOAD_CHUNKS)
            .set(DOCUMENT_UPLOAD_CHUNKS.ATTEMPTS, attempts + 1)
            .set(DOCUMENT_UPLOAD_CHUNKS.CURRENT_ATTEMPT_ID, attemptId)
            .set(DOCUMENT_UPLOAD_CHUNKS.OBJECT_KEY, key)
            .set(
                DOCUMENT_UPLOAD_CHUNKS.LEASE_UNTIL,
                DSL.field(
                    "clock_timestamp()+({0}*interval '1 second')",
                    java.time.OffsetDateTime::class.java,
                    DSL.`val`(seconds),
                ),
            )
            .where(
                DOCUMENT_UPLOAD_CHUNKS.COMPANY_ID.eq(companyId),
                DOCUMENT_UPLOAD_CHUNKS.REVISION_ID.eq(revisionId),
                DOCUMENT_UPLOAD_CHUNKS.ID.eq(id),
                DOCUMENT_UPLOAD_CHUNKS.ATTEMPTS.eq(attempts),
                DOCUMENT_UPLOAD_CHUNKS.STATUS.eq("PENDING"),
                DSL.condition("lease_until is null or lease_until<=clock_timestamp()"),
            )
            .returning()
            .fetchOne()

    override fun progress(
        companyId: UUID,
        revisionId: UUID,
        version: Long,
        offset: Long,
        size: Int,
    ) =
        sql.update(DOCUMENT_REVISIONS)
            .set(DOCUMENT_REVISIONS.UPLOADED_BYTES, offset + size)
            .set(DOCUMENT_REVISIONS.VERSION, version + 1)
            .where(
                DOCUMENT_REVISIONS.COMPANY_ID.eq(companyId),
                DOCUMENT_REVISIONS.ID.eq(revisionId),
                DOCUMENT_REVISIONS.VERSION.eq(version),
                DOCUMENT_REVISIONS.UPLOADED_BYTES.eq(offset),
                DOCUMENT_REVISIONS.STATUS.eq("UPLOADING"),
                DSL.condition("expires_at>clock_timestamp()"),
            )
            .returning()
            .fetchOne()

    override fun commitChunk(
        companyId: UUID,
        revisionId: UUID,
        id: UUID,
        attemptId: UUID,
        etag: String,
        version: Long,
    ) =
        sql.update(DOCUMENT_UPLOAD_CHUNKS)
            .set(DOCUMENT_UPLOAD_CHUNKS.STATUS, "COMMITTED")
            .set(DOCUMENT_UPLOAD_CHUNKS.ETAG, etag)
            .set(DOCUMENT_UPLOAD_CHUNKS.COMMITTED_VERSION, version)
            .setNull(DOCUMENT_UPLOAD_CHUNKS.LEASE_UNTIL)
            .where(
                DOCUMENT_UPLOAD_CHUNKS.COMPANY_ID.eq(companyId),
                DOCUMENT_UPLOAD_CHUNKS.REVISION_ID.eq(revisionId),
                DOCUMENT_UPLOAD_CHUNKS.ID.eq(id),
                DOCUMENT_UPLOAD_CHUNKS.CURRENT_ATTEMPT_ID.eq(attemptId),
                DOCUMENT_UPLOAD_CHUNKS.STATUS.eq("PENDING"),
                DSL.condition("lease_until>clock_timestamp()"),
            )
            .returning()
            .fetchOne()
}
