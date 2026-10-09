package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.Tables.WORK_PERIODS as P
import dev.fajar.hris.schema.Tables.WORK_PERIOD_SNAPSHOTS as S
import dev.fajar.hris.schema.tables.records.WorkPeriodsRecord
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

/** Payroll owns this read projection; factual workforce records are never changed here. */
class PostgresPayrollWorkSourceDataSource(private val sql: DSLContext) :
    PayrollWorkSourceDataSource {
    override fun period(company: UUID, month: LocalDate, lock: Boolean): WorkPeriodsRecord? {
        val query = sql.selectFrom(P).where(P.COMPANY_ID.eq(company), P.MONTH.eq(month))
        return if (lock) query.forShare().fetchOne() else query.fetchOne()
    }

    override fun includesEmployee(company: UUID, job: UUID, employee: UUID): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(S)
                .where(S.COMPANY_ID.eq(company), S.JOB_ID.eq(job), S.EMPLOYMENT_ID.eq(employee))
        )
}
