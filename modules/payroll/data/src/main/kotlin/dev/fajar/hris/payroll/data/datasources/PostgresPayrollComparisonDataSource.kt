package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import java.math.BigDecimal
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

class PostgresPayrollComparisonDataSource(private val sql: DSLContext) :
    PayrollComparisonDataSource {
    override fun compare(
        company: UUID,
        baseline: UUID,
        current: UUID,
        after: UUID?,
        limit: Int,
        changedOnly: Boolean,
    ): PayrollComparisonRows {
        val rows =
            sql.fetch(
                """
            WITH baseline AS MATERIALIZED (
                SELECT t.employment_id,t.employee_number,t.employee_name,r.status,r.taxable_gross,r.withheld,r.take_home
                FROM payroll_run_targets t LEFT JOIN payroll_run_results r USING(company_id,run_id,ordinal)
                WHERE t.company_id=? AND t.run_id=? ORDER BY t.ordinal LIMIT 5001
            ), current_run AS MATERIALIZED (
                SELECT t.employment_id,t.employee_number,t.employee_name,r.status,r.taxable_gross,r.withheld,r.take_home
                FROM payroll_run_targets t LEFT JOIN payroll_run_results r USING(company_id,run_id,ordinal)
                WHERE t.company_id=? AND t.run_id=? ORDER BY t.ordinal LIMIT 5001
            ), paired AS MATERIALIZED (
                SELECT coalesce(b.employment_id,c.employment_id) AS employment_id,
                    b.employment_id IS NOT NULL AS has_baseline,c.employment_id IS NOT NULL AS has_current,
                    b.employee_number AS baseline_number,b.employee_name AS baseline_name,c.employee_number AS current_number,c.employee_name AS current_name,
                    b.taxable_gross AS baseline_gross,b.withheld AS baseline_withheld,b.take_home AS baseline_home,
                    c.taxable_gross AS current_gross,c.withheld AS current_withheld,c.take_home AS current_home,
                    CASE WHEN b.employment_id IS NULL THEN 'ADDED' WHEN c.employment_id IS NULL THEN 'REMOVED'
                        WHEN (b.taxable_gross,b.withheld,b.take_home) IS DISTINCT FROM (c.taxable_gross,c.withheld,c.take_home) THEN 'CHANGED' ELSE 'UNCHANGED' END AS change,
                    (b.employment_id IS NOT NULL AND (b.status IS DISTINCT FROM 'SUCCEEDED' OR b.taxable_gross IS NULL OR b.withheld IS NULL OR b.take_home IS NULL))
                        OR (c.employment_id IS NOT NULL AND (c.status IS DISTINCT FROM 'SUCCEEDED' OR c.taxable_gross IS NULL OR c.withheld IS NULL OR c.take_home IS NULL)) AS invalid
                FROM baseline b FULL JOIN current_run c USING(employment_id)
            ), totals AS (
                SELECT count(*) FILTER(WHERE has_baseline) AS baseline_count,count(*) FILTER(WHERE has_current) AS current_count,
                    count(*) FILTER(WHERE has_baseline AND has_current) AS matched,
                    count(*) FILTER(WHERE change='ADDED') AS added,count(*) FILTER(WHERE change='REMOVED') AS removed,
                    count(*) FILTER(WHERE change='CHANGED') AS changed,count(*) FILTER(WHERE change='UNCHANGED') AS unchanged,
                    count(*) FILTER(WHERE invalid) AS invalid_count,
                    coalesce(sum(baseline_gross),0) AS baseline_total_gross,coalesce(sum(baseline_withheld),0) AS baseline_total_withheld,coalesce(sum(baseline_home),0) AS baseline_total_home,
                    coalesce(sum(current_gross),0) AS current_total_gross,coalesce(sum(current_withheld),0) AS current_total_withheld,coalesce(sum(current_home),0) AS current_total_home
                FROM paired
            ) SELECT s.*,p.* FROM totals s LEFT JOIN LATERAL (
                SELECT * FROM paired WHERE (CAST(? AS uuid) IS NULL OR employment_id>CAST(? AS uuid)) AND (NOT ? OR change<>'UNCHANGED')
                ORDER BY employment_id LIMIT ?
            ) p ON true ORDER BY p.employment_id
        """,
                company,
                baseline,
                company,
                current,
                after,
                after,
                changedOnly,
                limit + 1,
            )
        val totals = rows.first()
        fun count(key: String) = totals.get(key, Int::class.java)!!
        return PayrollComparisonRows(
            count("baseline_count"),
            count("current_count"),
            count("matched"),
            count("added"),
            count("removed"),
            count("changed"),
            count("unchanged"),
            count("invalid_count"),
            amounts(totals, "baseline_total"),
            amounts(totals, "current_total"),
            rows.mapNotNull { row ->
                val id = row.get("employment_id", UUID::class.java) ?: return@mapNotNull null
                fun employee(prefix: String) =
                    if (row.get("has_" + prefix, Boolean::class.java) == true)
                        PayrollComparisonEmployeeRow(
                            row.get(prefix + "_number", String::class.java)!!,
                            row.get(prefix + "_name", String::class.java)!!,
                            amounts(row, prefix),
                        )
                    else null
                PayrollComparisonItemRow(
                    id,
                    employee("baseline"),
                    employee("current"),
                    row.get("change", String::class.java)!!,
                )
            },
        )
    }

    private fun amounts(row: Record, prefix: String) =
        PayrollComparisonAmountsRow(
            row.get(prefix + "_gross", BigDecimal::class.java)!!,
            row.get(prefix + "_withheld", BigDecimal::class.java)!!,
            row.get(prefix + "_home", BigDecimal::class.java)!!,
        )
}
