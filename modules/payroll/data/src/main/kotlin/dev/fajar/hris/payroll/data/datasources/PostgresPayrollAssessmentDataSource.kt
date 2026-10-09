package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollTaxAssessmentRow
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
}
