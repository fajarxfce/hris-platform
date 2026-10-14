package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollEmployeeRow
import dev.fajar.hris.payroll.data.models.PayrollInputEmployeeRow
import dev.fajar.hris.payroll.data.models.PayrollInputSummaryRow
import dev.fajar.hris.schema.tables.Employments.EMPLOYMENTS as E
import dev.fajar.hris.schema.tables.PayrollInputRevisions.PAYROLL_INPUT_REVISIONS as R
import dev.fajar.hris.schema.tables.PayrollInputs.PAYROLL_INPUTS as I
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollInputDataSource(private val sql: DSLContext) : PayrollInputDataSource {
    override fun employees(
        company: UUID,
        month: LocalDate,
        query: String,
        status: String?,
        after: String?,
        limit: Int,
    ): List<PayrollInputEmployeeRow> =
        sql.select(E.ID, E.EMPLOYEE_NUMBER, P.LEGAL_NAME, E.VERSION, I.VERSION, R.STATUS)
            .from(E)
            .join(P)
            .on(P.ID.eq(E.PERSON_ID))
            .leftJoin(I)
            .on(
                I.COMPANY_ID.eq(E.COMPANY_ID)
                    .and(I.EMPLOYMENT_ID.eq(E.ID))
                    .and(I.EARNINGS_MONTH.eq(month))
            )
            .leftJoin(R)
            .on(
                R.COMPANY_ID.eq(I.COMPANY_ID).and(R.INPUT_ID.eq(I.ID)).and(R.REVISION.eq(I.VERSION))
            )
            .where(E.COMPANY_ID.eq(company))
            .and(
                E.EMPLOYEE_NUMBER.containsIgnoreCase(query)
                    .or(P.LEGAL_NAME.containsIgnoreCase(query))
            )
            .and(after?.let { E.EMPLOYEE_NUMBER.gt(it) } ?: DSL.noCondition())
            .and(
                when (status) {
                    null -> DSL.noCondition()
                    "UNPREPARED" -> I.ID.isNull
                    else -> R.STATUS.eq(status)
                }
            )
            .orderBy(E.EMPLOYEE_NUMBER)
            .limit(limit)
            .fetch {
                PayrollInputEmployeeRow(
                    PayrollEmployeeRow(
                        requireNotNull(it.value1()),
                        requireNotNull(it.value2()),
                        requireNotNull(it.value3()),
                        requireNotNull(it.value4()),
                    ),
                    it.value5(),
                    it.value6(),
                )
            }

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
