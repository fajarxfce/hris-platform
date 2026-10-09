package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.BackgroundJobs.BACKGROUND_JOBS as J
import dev.fajar.hris.schema.tables.LeaveBatchAttempts.LEAVE_BATCH_ATTEMPTS as A
import dev.fajar.hris.schema.tables.LeaveBatchResults.LEAVE_BATCH_RESULTS as R
import dev.fajar.hris.schema.tables.LeaveBatchTargets.LEAVE_BATCH_TARGETS as T
import dev.fajar.hris.schema.tables.LeaveBatches.LEAVE_BATCHES as B
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveBatchDataSource(private val sql: DSLContext) : LeaveBatchDataSource {
    override fun lock(company: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "leave-batches:$company")
            .execute()
    }

    override fun active(company: UUID, type: UUID, kind: String, period: LocalDate): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(B)
                .join(J)
                .on(J.COMPANY_ID.eq(B.COMPANY_ID), J.ID.eq(B.JOB_ID))
                .where(
                    B.COMPANY_ID.eq(company),
                    B.TYPE_ID.eq(type),
                    B.KIND.eq(kind),
                    B.PERIOD.eq(period),
                    B.STATUS.eq("RUNNING"),
                    J.STATUS.`in`("QUEUED", "RUNNING"),
                )
        )

    override fun find(company: UUID, id: UUID, lock: Boolean): LeaveBatchesRecord? {
        val query = sql.selectFrom(B).where(B.COMPANY_ID.eq(company), B.ID.eq(id))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun forJob(company: UUID, jobId: UUID, lock: Boolean): LeaveBatchesRecord? {
        val query = sql.selectFrom(B).where(B.COMPANY_ID.eq(company), B.JOB_ID.eq(jobId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun list(company: UUID, after: UUID?, limit: Int): List<LeaveBatchesRecord> =
        sql.selectFrom(B)
            .where(B.COMPANY_ID.eq(company))
            .and(after?.let { B.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(B.ID)
            .limit(limit)
            .fetch()

    override fun insert(row: LeaveBatchesRecord) {
        sql.insertInto(B).set(row).execute()
    }

    override fun insertTargets(rows: List<LeaveBatchTargetsRecord>) {
        sql.batchInsert(rows).execute()
    }

    override fun insertAttempt(row: LeaveBatchAttemptsRecord) {
        sql.insertInto(A).set(row).execute()
    }

    override fun next(company: UUID, batch: UUID): LeaveBatchTargetsRecord? =
        sql.selectFrom(T)
            .where(T.COMPANY_ID.eq(company), T.BATCH_ID.eq(batch))
            .andNotExists(
                sql.selectOne()
                    .from(R)
                    .where(
                        R.COMPANY_ID.eq(T.COMPANY_ID),
                        R.BATCH_ID.eq(T.BATCH_ID),
                        R.ORDINAL.eq(T.ORDINAL),
                    )
            )
            .orderBy(T.ORDINAL)
            .limit(1)
            .fetchOne()

    override fun outcome(row: LeaveBatchResultsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun counts(company: UUID, batch: UUID): Map<String, Int> =
        sql.select(R.STATUS, DSL.count())
            .from(R)
            .where(R.COMPANY_ID.eq(company), R.BATCH_ID.eq(batch))
            .groupBy(R.STATUS)
            .fetch { requireNotNull(it.value1()) to requireNotNull(it.value2()) }
            .toMap()

    override fun results(
        company: UUID,
        batch: UUID,
        after: Int?,
        limit: Int,
    ): List<LeaveBatchResultsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company), R.BATCH_ID.eq(batch))
            .and(after?.let { R.ORDINAL.gt(it) } ?: DSL.noCondition())
            .orderBy(R.ORDINAL)
            .limit(limit)
            .fetch()

    override fun attempts(company: UUID, batch: UUID): List<LeaveBatchAttemptsRecord> =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(company), A.BATCH_ID.eq(batch))
            .orderBy(A.ATTEMPT)
            .limit(8)
            .fetch()

    override fun transition(
        company: UUID,
        batch: UUID,
        version: Long,
        status: String,
        jobId: UUID,
    ): Long? =
        sql.update(B)
            .set(B.STATUS, status)
            .set(B.JOB_ID, jobId)
            .set(B.VERSION, version + 1)
            .where(B.COMPANY_ID.eq(company), B.ID.eq(batch), B.VERSION.eq(version))
            .returning(B.VERSION)
            .fetchOne()
            ?.version
}
