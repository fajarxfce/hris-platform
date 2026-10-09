package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollPeriodMemberRow
import dev.fajar.hris.schema.tables.PayrollInputRevisions.PAYROLL_INPUT_REVISIONS as R
import dev.fajar.hris.schema.tables.PayrollInputs.PAYROLL_INPUTS as I
import dev.fajar.hris.schema.tables.PayrollPeriodChanges.PAYROLL_PERIOD_CHANGES as C
import dev.fajar.hris.schema.tables.PayrollPeriodMembers.PAYROLL_PERIOD_MEMBERS as M
import dev.fajar.hris.schema.tables.PayrollPeriods.PAYROLL_PERIODS as P
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollPeriodDataSource(private val sql: DSLContext) : PayrollPeriodDataSource {
    override fun transition(
        company: UUID,
        id: UUID,
        version: Long,
        status: String,
        runId: UUID?,
    ): Long? =
        sql.update(P)
            .set(P.VERSION, version + 1)
            .set(P.STATUS, status)
            .set(P.CURRENT_RUN_ID, runId)
            .where(P.COMPANY_ID.eq(company), P.ID.eq(id), P.VERSION.eq(version))
            .returning(P.VERSION)
            .fetchOne()
            ?.version

    override fun find(company: UUID, id: UUID): PayrollPeriodsRecord? =
        sql.selectFrom(P).where(P.COMPANY_ID.eq(company), P.ID.eq(id)).fetchOne()

    override fun active(company: UUID, month: LocalDate): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(P)
                .where(
                    P.COMPANY_ID.eq(company),
                    P.EARNINGS_MONTH.eq(month),
                    P.STATUS.ne("CANCELLED"),
                )
        )

    override fun count(company: UUID, month: LocalDate): Int =
        sql.fetchCount(P, P.COMPANY_ID.eq(company).and(P.EARNINGS_MONTH.eq(month)))

    override fun insert(row: PayrollPeriodsRecord) {
        sql.insertInto(P).set(row).execute()
    }

    override fun insertMembers(rows: List<PayrollPeriodMembersRecord>) {
        for (chunk in rows.chunked(256)) sql.batchInsert(chunk).execute()
    }

    override fun cancel(company: UUID, id: UUID, version: Long): Long? =
        sql.update(P)
            .set(P.STATUS, "CANCELLED")
            .set(P.VERSION, version + 1)
            .where(
                P.COMPANY_ID.eq(company),
                P.ID.eq(id),
                P.VERSION.eq(version),
                P.STATUS.eq("DRAFT"),
            )
            .returning(P.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: PayrollPeriodChangesRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun members(
        company: UUID,
        id: UUID,
        after: UUID?,
        limit: Int,
    ): List<PayrollPeriodMemberRow> =
        sql.select(M.EMPLOYMENT_ID, I.VERSION, R.STATUS)
            .from(M)
            .join(P)
            .on(P.COMPANY_ID.eq(M.COMPANY_ID).and(P.ID.eq(M.PERIOD_ID)))
            .leftJoin(I)
            .on(
                I.COMPANY_ID.eq(M.COMPANY_ID)
                    .and(I.EMPLOYMENT_ID.eq(M.EMPLOYMENT_ID))
                    .and(I.EARNINGS_MONTH.eq(P.EARNINGS_MONTH))
            )
            .leftJoin(R)
            .on(
                R.COMPANY_ID.eq(I.COMPANY_ID).and(R.INPUT_ID.eq(I.ID)).and(R.REVISION.eq(I.VERSION))
            )
            .where(
                M.COMPANY_ID.eq(company),
                M.PERIOD_ID.eq(id),
                after?.let { M.EMPLOYMENT_ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(M.EMPLOYMENT_ID)
            .limit(limit)
            .fetch {
                PayrollPeriodMemberRow(
                    it.get(M.EMPLOYMENT_ID)!!,
                    it.get(I.VERSION),
                    it.get(R.STATUS),
                )
            }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<PayrollPeriodChangesRecord> =
        sql.selectFrom(C)
            .where(
                C.COMPANY_ID.eq(company),
                C.PERIOD_ID.eq(id),
                after?.let { C.REVISION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(C.REVISION)
            .limit(limit)
            .fetch()

    override fun list(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollPeriodsRecord> =
        sql.selectFrom(P)
            .where(
                P.COMPANY_ID.eq(company),
                P.EARNINGS_MONTH.between(from, until),
                status?.let { P.STATUS.eq(it) } ?: DSL.noCondition(),
                after?.let { P.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(P.ID)
            .limit(limit)
            .fetch()
}
