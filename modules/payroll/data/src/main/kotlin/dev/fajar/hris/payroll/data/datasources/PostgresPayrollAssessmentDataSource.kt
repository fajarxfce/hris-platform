package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.mappers.toPayslipSummaryRow
import dev.fajar.hris.payroll.data.models.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresPayrollAssessmentDataSource(private val sql: DSLContext) :
    PayrollAssessmentDataSource {
    override fun taxHistory(company: UUID, id: UUID): PayrollTaxAssessmentRow? =
        sql.fetchOne(
                """
                SELECT a.id,a.employment_id,a.tax_month,t.tax_opening_id,t.tax_opening_revision,
                  (o.facts->'compensation'->'tax')::text registration,
                  (o.calculation->'taxInput')::text tax_input,(o.calculation->'tax')::text tax_result
                FROM payroll_assessments a
                  JOIN payroll_finalizations f ON f.company_id=a.company_id AND f.id=a.finalization_id AND f.published_at IS NOT NULL
                  JOIN payroll_run_targets t ON t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal
                  JOIN payroll_run_results o ON o.company_id=a.company_id AND o.run_id=a.run_id AND o.ordinal=a.ordinal
                WHERE a.company_id=? AND a.id=?
                """
                    .trimIndent(),
                company,
                id,
            )
            ?.let {
                PayrollTaxAssessmentRow(
                    it.get("id", UUID::class.java)!!,
                    it.get("employment_id", UUID::class.java)!!,
                    it.get("tax_month", LocalDate::class.java)!!,
                    it.get("tax_opening_id", UUID::class.java)!!,
                    it.get("tax_opening_revision", Long::class.javaObjectType)!!,
                    it.get("registration", String::class.java)!!,
                    it.get("tax_input", String::class.java)!!,
                    it.get("tax_result", String::class.java)!!,
                )
            }

    override fun payslip(
        company: UUID,
        id: UUID,
        allowedEmployees: Set<UUID>?,
    ): PayrollPayslipRow? =
        sql.fetchOne(
                """
                WITH selected AS MATERIALIZED (
                  SELECT a.* FROM payroll_assessments a WHERE a.company_id=? AND a.id=?
                    AND (?::uuid[] IS NULL OR a.employment_id=ANY(?::uuid[])) LIMIT 1
                )
                SELECT a.id,a.employment_id,a.tax_month,a.published_at,t.employee_number,t.employee_name,r.planned_payment_date,
                  o.taxable_gross,o.withheld,o.take_home,o.calculation::text calculation,f.company_code,f.company_name
                FROM selected a
                  CROSS JOIN LATERAL(SELECT t.employee_number,t.employee_name FROM payroll_run_targets t
                    WHERE t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal LIMIT 1) t
                  CROSS JOIN LATERAL(SELECT o.taxable_gross,o.withheld,o.take_home,o.calculation FROM payroll_run_results o
                    WHERE o.company_id=a.company_id AND o.run_id=a.run_id AND o.ordinal=a.ordinal LIMIT 1) o
                  CROSS JOIN LATERAL(SELECT r.planned_payment_date FROM payroll_runs r WHERE r.company_id=a.company_id AND r.id=a.run_id LIMIT 1) r
                  CROSS JOIN LATERAL(SELECT f.company_code,f.company_name FROM payroll_finalizations f
                    WHERE f.company_id=a.company_id AND f.id=a.finalization_id AND f.published_at IS NOT NULL LIMIT 1) f
                """
                    .trimIndent(),
                company,
                id,
                allowedEmployees?.toTypedArray(),
                allowedEmployees?.toTypedArray(),
            )
            ?.let {
                PayrollPayslipRow(
                    it.toPayslipSummaryRow(),
                    it.get("company_code", String::class.java)!!,
                    it.get("company_name", String::class.java)!!,
                    it.get("calculation", String::class.java)!!,
                )
            }

    override fun payslips(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        allowedEmployees: Set<UUID>?,
        beforeMonth: LocalDate?,
        afterId: UUID?,
        limit: Int,
    ): List<PayrollPayslipSummaryRow> =
        sql.fetch(
                """
                WITH selected AS MATERIALIZED (
                  SELECT a.* FROM payroll_assessments a WHERE a.company_id=? AND a.tax_month BETWEEN ? AND ?
                    AND (?::uuid IS NULL OR a.employment_id=?::uuid)
                    AND (?::uuid[] IS NULL OR a.employment_id=ANY(?::uuid[]))
                    AND (?::date IS NULL OR a.tax_month<?::date OR (a.tax_month=?::date AND a.id>?::uuid))
                  ORDER BY a.tax_month DESC,a.id LIMIT ?
                )
                SELECT a.id,a.employment_id,a.tax_month,a.published_at,t.employee_number,t.employee_name,r.planned_payment_date,
                  o.taxable_gross,o.withheld,o.take_home
                FROM selected a
                  CROSS JOIN LATERAL(SELECT t.employee_number,t.employee_name FROM payroll_run_targets t
                    WHERE t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal LIMIT 1) t
                  CROSS JOIN LATERAL(SELECT o.taxable_gross,o.withheld,o.take_home FROM payroll_run_results o
                    WHERE o.company_id=a.company_id AND o.run_id=a.run_id AND o.ordinal=a.ordinal LIMIT 1) o
                  CROSS JOIN LATERAL(SELECT r.planned_payment_date FROM payroll_runs r WHERE r.company_id=a.company_id AND r.id=a.run_id LIMIT 1) r
                ORDER BY a.tax_month DESC,a.id
                """
                    .trimIndent(),
                company,
                from,
                until,
                employeeId,
                employeeId,
                allowedEmployees?.toTypedArray(),
                allowedEmployees?.toTypedArray(),
                beforeMonth,
                beforeMonth,
                beforeMonth,
                afterId,
                limit,
            )
            .map { it.toPayslipSummaryRow() }
}
