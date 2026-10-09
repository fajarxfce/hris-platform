package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollInputSummaryRow
import dev.fajar.hris.schema.tables.PayrollInputRevisions.PAYROLL_INPUT_REVISIONS as R
import dev.fajar.hris.schema.tables.PayrollInputs.PAYROLL_INPUTS as I
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollInputDataSource(private val sql: DSLContext) : PayrollInputDataSource {
    override fun find(
        company: UUID,
        employee: UUID,
        month: LocalDate,
    ): PayrollInputRevisionsRecord? =
        sql.select(*R.fields())
            .from(I)
            .join(R)
            .on(
                R.COMPANY_ID.eq(I.COMPANY_ID).and(R.INPUT_ID.eq(I.ID)).and(R.REVISION.eq(I.VERSION))
            )
            .where(
                I.COMPANY_ID.eq(company),
                I.EMPLOYMENT_ID.eq(employee),
                I.EARNINGS_MONTH.eq(month),
            )
            .fetchOne { it.into(R) }

    override fun revision(
        company: UUID,
        employee: UUID,
        month: LocalDate,
        revision: Long,
    ): PayrollInputRevisionsRecord? =
        sql.select(*R.fields())
            .from(I)
            .join(R)
            .on(R.COMPANY_ID.eq(I.COMPANY_ID).and(R.INPUT_ID.eq(I.ID)))
            .where(
                I.COMPANY_ID.eq(company),
                I.EMPLOYMENT_ID.eq(employee),
                I.EARNINGS_MONTH.eq(month),
                R.REVISION.eq(revision),
            )
            .fetchOne { it.into(R) }

    override fun list(
        company: UUID,
        month: LocalDate,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollInputSummaryRow> =
        sql.select(
                R.INPUT_ID,
                R.EMPLOYMENT_ID,
                R.EARNINGS_MONTH,
                R.REVISION,
                R.STATUS,
                R.PREPARED_BY,
                R.VERIFIED_BY,
                R.RECORDED_AT,
                R.REASON,
            )
            .from(I)
            .join(R)
            .on(
                R.COMPANY_ID.eq(I.COMPANY_ID).and(R.INPUT_ID.eq(I.ID)).and(R.REVISION.eq(I.VERSION))
            )
            .where(
                I.COMPANY_ID.eq(company),
                I.EARNINGS_MONTH.eq(month),
                status?.let { R.STATUS.eq(it) } ?: DSL.noCondition(),
                after?.let { I.EMPLOYMENT_ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(I.EMPLOYMENT_ID)
            .limit(limit)
            .fetch {
                PayrollInputSummaryRow(
                    it.get(R.INPUT_ID)!!,
                    it.get(R.EMPLOYMENT_ID)!!,
                    it.get(R.EARNINGS_MONTH)!!,
                    it.get(R.REVISION)!!,
                    it.get(R.STATUS)!!,
                    it.get(R.PREPARED_BY)!!,
                    it.get(R.VERIFIED_BY),
                    it.get(R.RECORDED_AT)!!,
                    it.get(R.REASON)!!,
                )
            }

    override fun history(
        company: UUID,
        employee: UUID,
        month: LocalDate,
        after: Long?,
        limit: Int,
    ): List<PayrollInputSummaryRow> =
        sql.select(
                R.INPUT_ID,
                R.EMPLOYMENT_ID,
                R.EARNINGS_MONTH,
                R.REVISION,
                R.STATUS,
                R.PREPARED_BY,
                R.VERIFIED_BY,
                R.RECORDED_AT,
                R.REASON,
            )
            .from(I)
            .join(R)
            .on(R.COMPANY_ID.eq(I.COMPANY_ID).and(R.INPUT_ID.eq(I.ID)))
            .where(
                I.COMPANY_ID.eq(company),
                I.EMPLOYMENT_ID.eq(employee),
                I.EARNINGS_MONTH.eq(month),
                after?.let { R.REVISION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(R.REVISION)
            .limit(limit)
            .fetch {
                PayrollInputSummaryRow(
                    it.get(R.INPUT_ID)!!,
                    it.get(R.EMPLOYMENT_ID)!!,
                    it.get(R.EARNINGS_MONTH)!!,
                    it.get(R.REVISION)!!,
                    it.get(R.STATUS)!!,
                    it.get(R.PREPARED_BY)!!,
                    it.get(R.VERIFIED_BY),
                    it.get(R.RECORDED_AT)!!,
                    it.get(R.REASON)!!,
                )
            }

    override fun authors(company: UUID, id: UUID): List<UUID> =
        sql.selectDistinct(R.PREPARED_BY)
            .from(R)
            .where(R.COMPANY_ID.eq(company), R.INPUT_ID.eq(id), R.STATUS.eq("DRAFT"))
            .limit(1000)
            .fetch(R.PREPARED_BY)

    override fun insert(row: PayrollInputsRecord) {
        sql.insertInto(I).set(row).execute()
    }

    override fun advance(company: UUID, id: UUID, version: Long): Long? =
        sql.update(I)
            .set(I.VERSION, version + 1)
            .where(I.COMPANY_ID.eq(company), I.ID.eq(id), I.VERSION.eq(version))
            .returning(I.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: PayrollInputRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
