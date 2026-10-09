package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.tables.PayrollRunTargets.PAYROLL_RUN_TARGETS as T
import dev.fajar.hris.schema.tables.PayrollRuns.PAYROLL_RUNS as R
import java.time.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollCutoffDataSource(private val sql: DSLContext) : PayrollCutoffDataSource {
    override fun lock(company: UUID) {
        sql.query("select pg_advisory_xact_lock_shared(hashtextextended(?,0))", "payroll:$company")
            .execute()
    }

    override fun frozenMonths(company: UUID, employee: UUID?, months: Set<LocalDate>): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(R)
                .where(
                    R.COMPANY_ID.eq(company),
                    R.STATUS.ne("ABANDONED"),
                    R.EARNINGS_MONTH.`in`(months),
                )
                .and(
                    if (employee == null) DSL.noCondition()
                    else
                        DSL.exists(
                            sql.selectOne()
                                .from(T)
                                .where(
                                    T.COMPANY_ID.eq(R.COMPANY_ID),
                                    T.RUN_ID.eq(R.ID),
                                    T.EMPLOYMENT_ID.eq(employee),
                                )
                        )
                )
        )

    override fun frozenFrom(company: UUID, employee: UUID?, from: LocalDate): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(R)
                .where(
                    R.COMPANY_ID.eq(company),
                    R.STATUS.ne("ABANDONED"),
                    R.EARNINGS_MONTH.ge(from),
                )
                .and(
                    if (employee == null) DSL.noCondition()
                    else
                        DSL.exists(
                            sql.selectOne()
                                .from(T)
                                .where(
                                    T.COMPANY_ID.eq(R.COMPANY_ID),
                                    T.RUN_ID.eq(R.ID),
                                    T.EMPLOYMENT_ID.eq(employee),
                                )
                        )
                )
        )
}
