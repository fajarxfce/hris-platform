package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.WorkPeriodSnapshots.WORK_PERIOD_SNAPSHOTS as S
import dev.fajar.hris.schema.tables.WorkPeriods.WORK_PERIODS as W
import dev.fajar.hris.schema.tables.records.WorkPeriodsRecord
import java.time.*
import java.util.UUID
import org.jooq.DSLContext

class PostgresPayrollCalculationSourceDataSource(private val sql: DSLContext) :
    PayrollCalculationSourceDataSource {
    override fun workPeriod(company: UUID, month: LocalDate, lock: Boolean): WorkPeriodsRecord? {
        val query = sql.selectFrom(W).where(W.COMPANY_ID.eq(company), W.MONTH.eq(month))
        return if (lock) query.forShare().fetchOne() else query.fetchOne()
    }

    override fun targets(company: UUID, period: UUID, month: LocalDate): List<PayrollRunTargetRow> =
        sql.resultQuery(
                """
                WITH selected AS MATERIALIZED (
                    SELECT m.employment_id FROM payroll_period_members m
                    WHERE m.company_id=? AND m.period_id=? AND EXISTS (
                        SELECT 1 FROM payroll_periods pp
                        WHERE pp.company_id=? AND pp.id=? AND pp.earnings_month=?
                    ) ORDER BY m.employment_id LIMIT 5001
                )
                SELECT e.id,e.employee_number,p.legal_name,e.version,c.revision compensation_revision,
                    i.id input_id,i.version input_revision,o.id opening_id,o.version opening_revision
                FROM selected m
                JOIN LATERAL (
                    SELECT e.id,e.employee_number,e.person_id,e.version FROM employments e
                    WHERE e.company_id=? AND e.id=m.employment_id LIMIT 1
                ) e ON true
                JOIN LATERAL (
                    SELECT p.legal_name FROM persons p WHERE p.id=e.person_id LIMIT 1
                ) p ON true
                LEFT JOIN LATERAL (
                    SELECT c.revision FROM employee_compensation_revisions c
                    WHERE c.company_id=? AND c.employment_id=e.id AND c.effective_from<=?
                    ORDER BY c.effective_from DESC,c.revision DESC LIMIT 1
                ) c ON true
                LEFT JOIN LATERAL (
                    SELECT i.id,i.version FROM payroll_inputs i
                    WHERE i.company_id=? AND i.employment_id=e.id AND i.earnings_month=? LIMIT 1
                ) i ON true
                LEFT JOIN LATERAL (
                    SELECT o.id,o.version FROM payroll_tax_openings o
                    WHERE o.company_id=? AND o.employment_id=e.id AND o.tax_year=? LIMIT 1
                ) o ON true
                ORDER BY e.id
                """,
                company,
                period,
                company,
                period,
                month,
                company,
                company,
                month,
                company,
                month,
                company,
                month.year,
            )
            .fetch { r ->
                PayrollRunTargetRow(
                    r.get("id", UUID::class.java)!!,
                    r.get("employee_number", String::class.java)!!,
                    r.get("legal_name", String::class.java)!!,
                    r.get("version", Long::class.javaObjectType)!!,
                    r.get("compensation_revision", Long::class.javaObjectType),
                    r.get("input_id", UUID::class.java),
                    r.get("input_revision", Long::class.javaObjectType),
                    r.get("opening_id", UUID::class.java),
                    r.get("opening_revision", Long::class.javaObjectType),
                )
            }

    override fun pendingLeave(company: UUID, period: UUID, month: LocalDate): Boolean =
        sql.fetchValue(
            """
 SELECT EXISTS(SELECT 1 FROM leave_requests l JOIN payroll_period_members m ON m.company_id=l.company_id AND m.employment_id=l.employment_id
 WHERE m.company_id=? AND m.period_id=? AND l.status IN('PENDING','CANCELLATION_PENDING') AND
 EXISTS(SELECT 1 FROM jsonb_array_elements(l.days)d WHERE date_trunc('month',(d->>'workDate')::date)::date=?))
 """,
            company,
            period,
            month,
        ) as Boolean

    override fun workSnapshot(company: UUID, job: UUID, employee: UUID): String? =
        sql.select(S.PAYLOAD)
            .from(S)
            .where(S.COMPANY_ID.eq(company), S.JOB_ID.eq(job), S.EMPLOYMENT_ID.eq(employee))
            .fetchOne(S.PAYLOAD)
            ?.data()

    override fun leaveDays(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<PayrollLeaveDayRow> =
        sql.resultQuery(
                """
 SELECT l.id,l.version,(d->>'workDate')::date work_date,d->>'portion' portion,(l.type_snapshot->'policy'->>'paid')::boolean paid
 FROM leave_requests l CROSS JOIN LATERAL jsonb_array_elements(l.days)d
 WHERE l.company_id=? AND l.employment_id=? AND l.status='APPROVED' AND (d->>'workDate')::date BETWEEN ? AND ?
 ORDER BY work_date,portion,l.id LIMIT 63
 """,
                company,
                employee,
                from,
                until,
            )
            .fetch { r ->
                PayrollLeaveDayRow(
                    r.get("id", UUID::class.java)!!,
                    r.get("version", Long::class.javaObjectType)!!,
                    r.get("work_date", LocalDate::class.java)!!,
                    r.get("portion", String::class.java)!!,
                    r.get("paid", Boolean::class.javaObjectType)!!,
                )
            }
}
