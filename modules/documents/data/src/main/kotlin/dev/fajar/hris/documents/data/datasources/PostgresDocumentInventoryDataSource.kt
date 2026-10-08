package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.models.*
import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresDocumentInventoryDataSource(private val sql: DSLContext) :
    DocumentInventoryDataSource {
    override fun capacity(companyId: UUID): DocumentInventoryCapacityData {
        val r = DOCUMENT_INVENTORY_VIEWS
        val row =
            sql.select(
                    DSL.count().cast(Long::class.java),
                    DSL.count()
                        .filterWhere(
                            r.STATUS.eq("SCANNING"),
                            r.JOB_STATUS.`in`("QUEUED", "RUNNING"),
                        )
                        .cast(Long::class.java),
                )
                .from(r)
                .where(r.COMPANY_ID.eq(companyId))
                .fetchSingle()
        return DocumentInventoryCapacityData(row.value1(), row.value2())
    }

    override fun find(companyId: UUID, id: UUID) =
        sql.selectFrom(DOCUMENT_INVENTORY_VIEWS)
            .where(
                DOCUMENT_INVENTORY_VIEWS.COMPANY_ID.eq(companyId),
                DOCUMENT_INVENTORY_VIEWS.ID.eq(id),
            )
            .fetchOne()

    override fun list(companyId: UUID, after: UUID?, limit: Int) =
        sql.selectFrom(DOCUMENT_INVENTORY_VIEWS)
            .where(
                DOCUMENT_INVENTORY_VIEWS.COMPANY_ID.eq(companyId),
                after?.let { DOCUMENT_INVENTORY_VIEWS.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(DOCUMENT_INVENTORY_VIEWS.ID)
            .limit(limit)
            .fetch()

    override fun insert(row: DocumentInventoryRunsRecord) {
        sql.insertInto(DOCUMENT_INVENTORY_RUNS).set(row).execute()
    }

    override fun insertAttempt(row: DocumentInventoryAttemptsRecord) {
        sql.insertInto(DOCUMENT_INVENTORY_ATTEMPTS).set(row).execute()
    }

    override fun resume(
        companyId: UUID,
        id: UUID,
        version: Long,
        jobId: UUID,
        attempt: Int,
    ): Boolean {
        val r = DOCUMENT_INVENTORY_RUNS
        return sql.update(r)
            .set(r.JOB_ID, jobId)
            .set(r.ATTEMPTS, attempt)
            .set(r.VERSION, version + 1)
            .where(
                r.COMPANY_ID.eq(companyId),
                r.ID.eq(id),
                r.VERSION.eq(version),
                r.STATUS.eq("SCANNING"),
            )
            .execute() == 1
    }

    override fun references(
        companyId: UUID,
        keys: Set<String>,
    ): List<DocumentInventoryReferenceData> {
        require(keys.size <= 100)
        if (keys.isEmpty()) return emptyList()
        val a = DOCUMENT_UPLOAD_ATTEMPTS
        val c = DOCUMENT_UPLOAD_CHUNKS
        val r = DOCUMENT_REVISIONS
        val q = OBJECT_CLEANUP_QUEUE
        val h = DOCUMENT_INVENTORY_RECOVERIES
        val count =
            DSL.selectCount()
                .from(h)
                .where(h.COMPANY_ID.eq(a.COMPANY_ID), h.ATTEMPT_ID.eq(a.ID))
                .asField<Int>()
        return sql.select(
                a.OBJECT_KEY,
                a.ID,
                a.REVISION_ID,
                a.BYTE_COUNT,
                c.CURRENT_ATTEMPT_ID,
                r.STATUS,
                r.EXPIRES_AT,
                q.ID.isNotNull,
                count,
            )
            .from(a)
            .join(c)
            .on(c.COMPANY_ID.eq(a.COMPANY_ID), c.REVISION_ID.eq(a.REVISION_ID), c.ID.eq(a.CHUNK_ID))
            .join(r)
            .on(r.COMPANY_ID.eq(a.COMPANY_ID), r.ID.eq(a.REVISION_ID))
            .leftJoin(q)
            .on(q.COMPANY_ID.eq(a.COMPANY_ID), q.OBJECT_KEY.eq(a.OBJECT_KEY))
            .where(a.COMPANY_ID.eq(companyId), a.OBJECT_KEY.`in`(keys))
            .limit(100)
            .fetch {
                DocumentInventoryReferenceData(
                    it.value1(),
                    it.value2(),
                    it.value3(),
                    it.value4().toLong(),
                    it.value5(),
                    it.value6(),
                    it.value7(),
                    it.value8(),
                    it.value9(),
                )
            }
    }

    override fun insertRecovery(row: DocumentInventoryRecoveriesRecord) {
        sql.insertInto(DOCUMENT_INVENTORY_RECOVERIES).set(row).execute()
    }

    override fun checkpoint(
        companyId: UUID,
        id: UUID,
        version: Long,
        row: DocumentInventoryPagesRecord,
        status: String,
    ): Boolean {
        sql.insertInto(DOCUMENT_INVENTORY_PAGES).set(row).execute()
        val r = DOCUMENT_INVENTORY_RUNS
        return sql.update(r)
            .set(r.VERSION, version + 1)
            .set(r.PAGES, row.pageNo)
            .set(r.LAST_KEY, row.lastKey)
            .set(r.STATUS, status)
            .set(r.FINISHED_AT, if (status == "SCANNING") null else row.createdAt)
            .set(r.RETAINED, r.RETAINED.plus(row.retained))
            .set(r.UNKNOWN, r.UNKNOWN.plus(row.unknown))
            .set(r.ANOMALOUS, r.ANOMALOUS.plus(row.anomalous))
            .set(r.QUEUED, r.QUEUED.plus(row.queued))
            .set(r.RECOVERY_EXHAUSTED, r.RECOVERY_EXHAUSTED.plus(row.recoveryExhausted))
            .set(r.SCHEDULED, r.SCHEDULED.plus(row.scheduled))
            .where(
                r.COMPANY_ID.eq(companyId),
                r.ID.eq(id),
                r.VERSION.eq(version),
                r.JOB_ID.eq(row.jobId),
                r.STATUS.eq("SCANNING"),
            )
            .execute() == 1
    }

    override fun attempts(companyId: UUID, id: UUID) =
        sql.selectFrom(DOCUMENT_INVENTORY_ATTEMPT_VIEWS)
            .where(
                DOCUMENT_INVENTORY_ATTEMPT_VIEWS.COMPANY_ID.eq(companyId),
                DOCUMENT_INVENTORY_ATTEMPT_VIEWS.RUN_ID.eq(id),
            )
            .orderBy(DOCUMENT_INVENTORY_ATTEMPT_VIEWS.ATTEMPT)
            .limit(8)
            .fetch()

    override fun pages(companyId: UUID, id: UUID, after: Int, limit: Int) =
        sql.selectFrom(DOCUMENT_INVENTORY_PAGES)
            .where(
                DOCUMENT_INVENTORY_PAGES.COMPANY_ID.eq(companyId),
                DOCUMENT_INVENTORY_PAGES.RUN_ID.eq(id),
                DOCUMENT_INVENTORY_PAGES.PAGE_NO.gt(after),
            )
            .orderBy(DOCUMENT_INVENTORY_PAGES.PAGE_NO)
            .limit(limit)
            .fetch()
}
