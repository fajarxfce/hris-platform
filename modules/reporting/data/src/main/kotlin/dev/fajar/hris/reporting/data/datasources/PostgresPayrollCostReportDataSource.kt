package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.PayrollCostReportAggregateRow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresPayrollCostReportDataSource(private val sql: DSLContext) :
    PayrollCostReportDataSource {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<PayrollCostReportAggregateRow> =
        sql.fetch(
                """
                with selected as (
                  select a.tax_month,a.employment_id,h.revision,
                    (e.calculation->>'employerCost')::numeric employer_cost,
                    (e.calculation->'tax'->>'taxableGross')::numeric taxable_gross,
                    (e.calculation->>'employeeDeductions')::numeric employee_deductions,
                    (e.calculation->'tax'->>'withheld')::numeric withheld,
                    (e.calculation->'tax'->>'takeHome')::numeric take_home
                  from payroll_assessments a
                    join payroll_finalizations f on f.company_id=a.company_id and f.id=a.finalization_id and f.published_at is not null
                    join payroll_assessment_heads h on h.company_id=a.company_id and h.assessment_id=a.id
                    left join payroll_assessment_evidence e on e.company_id=h.company_id and e.assessment_id=h.assessment_id and e.revision=h.revision
                  where a.company_id=? and a.tax_month between ? and ?
                )
                select tax_month as month,count(*) as assessments,count(distinct employment_id) as employments,
                  count(*) filter(where revision>0) as revised_assessments,
                  coalesce(sum(employer_cost),0) as employer_cost,
                  coalesce(sum(taxable_gross),0) as taxable_gross,
                  coalesce(sum(employee_deductions),0) as employee_deductions,
                  coalesce(sum(withheld),0) as withheld,coalesce(sum(take_home),0) as take_home,
                  count(*) filter(where employer_cost is null or taxable_gross is null or employee_deductions is null
                    or withheld is null or take_home is null) as missing_values
                from selected group by grouping sets ((),(tax_month))
                """
                    .trimIndent(),
                company,
                from,
                until,
            )
            .map { row ->
                PayrollCostReportAggregateRow(
                    row.get("month", LocalDate::class.java),
                    requireNotNull(row.get("assessments", Long::class.java)),
                    requireNotNull(row.get("employments", Long::class.java)),
                    requireNotNull(row.get("revised_assessments", Long::class.java)),
                    requireNotNull(row.get("employer_cost", BigDecimal::class.java)),
                    requireNotNull(row.get("taxable_gross", BigDecimal::class.java)),
                    requireNotNull(row.get("employee_deductions", BigDecimal::class.java)),
                    requireNotNull(row.get("withheld", BigDecimal::class.java)),
                    requireNotNull(row.get("take_home", BigDecimal::class.java)),
                    requireNotNull(row.get("missing_values", Long::class.java)),
                )
            }
}
