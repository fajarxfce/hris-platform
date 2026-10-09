package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.PayrollRunAttempts.PAYROLL_RUN_ATTEMPTS as A
import dev.fajar.hris.schema.tables.PayrollRunResults.PAYROLL_RUN_RESULTS as O
import dev.fajar.hris.schema.tables.PayrollRunTargets.PAYROLL_RUN_TARGETS as T
import dev.fajar.hris.schema.tables.PayrollRuns.PAYROLL_RUNS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollRunDataSource(private val sql: DSLContext) : PayrollRunDataSource {
    override fun find(company: UUID, id: UUID, lock: Boolean): PayrollRunsRecord? {
        val query = sql.selectFrom(R).where(R.COMPANY_ID.eq(company), R.ID.eq(id))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun forJob(company: UUID, job: UUID, lock: Boolean): PayrollRunsRecord? {
        val query = sql.selectFrom(R).where(R.COMPANY_ID.eq(company), R.JOB_ID.eq(job))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun count(company: UUID, period: UUID): Int =
        sql.fetchCount(
            sql.selectOne().from(R).where(R.COMPANY_ID.eq(company), R.PERIOD_ID.eq(period))
        )

    override fun list(
        company: UUID,
        period: UUID,
        after: Int?,
        limit: Int,
    ): List<PayrollRunsRecord> =
        sql.selectFrom(R)
            .where(
                R.COMPANY_ID.eq(company),
                R.PERIOD_ID.eq(period),
                after?.let { R.RUN_NUMBER.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(R.RUN_NUMBER)
            .limit(limit)
            .fetch()

    override fun insert(row: PayrollRunsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun insertTargets(rows: List<PayrollRunTargetsRecord>) {
        sql.batchInsert(rows).execute()
    }

    override fun target(company: UUID, run: UUID, ordinal: Int): PayrollRunTargetsRecord? =
        sql.selectFrom(T)
            .where(T.COMPANY_ID.eq(company), T.RUN_ID.eq(run), T.ORDINAL.eq(ordinal))
            .fetchOne()

    override fun result(company: UUID, run: UUID, employee: UUID): PayrollRunResultRow? =
        sql.select(*T.fields(), *O.fields())
            .from(T)
            .join(O)
            .on(
                O.COMPANY_ID.eq(T.COMPANY_ID)
                    .and(O.RUN_ID.eq(T.RUN_ID))
                    .and(O.ORDINAL.eq(T.ORDINAL))
            )
            .where(T.COMPANY_ID.eq(company), T.RUN_ID.eq(run), T.EMPLOYMENT_ID.eq(employee))
            .fetchOne { PayrollRunResultRow(it.into(T), it.into(O)) }

    override fun results(
        company: UUID,
        run: UUID,
        after: Int?,
        limit: Int,
    ): List<PayrollRunItemRow> =
        sql.select(
                *T.fields(),
                O.JOB_ID,
                O.COMPLETED_AT,
                O.FAILURE_KIND,
                O.FAILURE_CODE,
                O.FAILURE_FIELDS,
                O.FAILURE_PARAMETERS,
                O.TAXABLE_GROSS,
                O.WITHHELD,
                O.TAKE_HOME,
            )
            .from(T)
            .join(O)
            .on(
                O.COMPANY_ID.eq(T.COMPANY_ID)
                    .and(O.RUN_ID.eq(T.RUN_ID))
                    .and(O.ORDINAL.eq(T.ORDINAL))
            )
            .where(
                T.COMPANY_ID.eq(company),
                T.RUN_ID.eq(run),
                after?.let { T.ORDINAL.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(T.ORDINAL)
            .limit(limit)
            .fetch {
                PayrollRunItemRow(
                    it.into(T),
                    it.get(O.JOB_ID)!!,
                    it.get(O.COMPLETED_AT)!!,
                    it.get(O.FAILURE_KIND),
                    it.get(O.FAILURE_CODE),
                    it.get(O.FAILURE_FIELDS)!!,
                    it.get(O.FAILURE_PARAMETERS)!!,
                    it.get(O.TAXABLE_GROSS),
                    it.get(O.WITHHELD),
                    it.get(O.TAKE_HOME),
                )
            }

    override fun insertResult(row: PayrollRunResultsRecord) {
        sql.insertInto(O).set(row).execute()
    }

    override fun advanceProgress(
        company: UUID,
        run: UUID,
        processed: Int,
        success: Boolean,
    ): Boolean =
        sql.update(R)
            .set(R.PROCESSED, processed + 1)
            .set(R.SUCCEEDED, R.SUCCEEDED.plus(if (success) 1 else 0))
            .set(R.FAILED, R.FAILED.plus(if (success) 0 else 1))
            .where(
                R.COMPANY_ID.eq(company),
                R.ID.eq(run),
                R.PROCESSED.eq(processed),
                R.STATUS.eq("PROCESSING"),
            )
            .execute() == 1

    override fun transition(
        company: UUID,
        run: UUID,
        version: Long,
        status: String,
        job: UUID,
    ): Long? =
        sql.update(R)
            .set(R.VERSION, version + 1)
            .set(R.STATUS, status)
            .set(R.JOB_ID, job)
            .where(R.COMPANY_ID.eq(company), R.ID.eq(run), R.VERSION.eq(version))
            .returning(R.VERSION)
            .fetchOne()
            ?.version

    override fun attempts(company: UUID, run: UUID): List<PayrollRunAttemptsRecord> =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(company), A.RUN_ID.eq(run))
            .orderBy(A.ATTEMPT)
            .limit(8)
            .fetch()

    override fun insertAttempt(row: PayrollRunAttemptsRecord) {
        sql.insertInto(A).set(row).execute()
    }
}
