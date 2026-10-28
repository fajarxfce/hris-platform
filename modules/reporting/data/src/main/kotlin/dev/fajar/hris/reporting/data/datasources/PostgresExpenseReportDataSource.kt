package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresExpenseReportDataSource(private val sql: DSLContext) : ExpenseReportDataSource {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<ExpenseReportAggregateRow> =
        sql.fetch(
                """
            $matchingExpenseReportClaimsSql
            select status,count(*) as claims,count(distinct employment_id) as employments,
                coalesce(sum(lines),0)::bigint as lines,coalesce(sum(amount),0) as amount
            from matching_claims group by grouping sets ((),(status))
        """
                    .trimIndent(),
                company,
                from,
                until,
            )
            .map { row ->
                ExpenseReportAggregateRow(
                    row.get("status", String::class.java),
                    requireNotNull(row.get("claims", Long::class.java)),
                    requireNotNull(row.get("employments", Long::class.java)),
                    requireNotNull(row.get("lines", Long::class.java)),
                    requireNotNull(row.get("amount", BigDecimal::class.java)),
                )
            }

    override fun claims(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        afterAt: OffsetDateTime?,
        afterId: UUID?,
        limit: Int,
    ): List<ExpenseReportClaimRow> {
        val filters = mutableListOf<String>()
        val values = mutableListOf<Any>(company, from, until)
        if (status != null) {
            filters += "status=?"
            values += status
        }
        if (afterAt != null && afterId != null) {
            filters += "(submitted_at,id)<(?::timestamptz,?::uuid)"
            values += afterAt
            values += afterId
        }
        values += limit
        val where = if (filters.isEmpty()) "" else "where ${filters.joinToString(" and ")}"
        return sql.fetch(
                """
            $matchingExpenseReportClaimsSql
            select id,submission_id,submission_number,employment_id,employee_number,employee_name,title,
                status,submitted_at,version,lines,amount,submission_amount
            from matching_claims $where order by submitted_at desc,id desc limit ?
        """
                    .trimIndent(),
                *values.toTypedArray(),
            )
            .map { row ->
                ExpenseReportClaimRow(
                    requireNotNull(row.get("id", UUID::class.java)),
                    requireNotNull(row.get("submission_id", UUID::class.java)),
                    requireNotNull(row.get("submission_number", Int::class.java)),
                    requireNotNull(row.get("employment_id", UUID::class.java)),
                    requireNotNull(row.get("employee_number", String::class.java)),
                    requireNotNull(row.get("employee_name", String::class.java)),
                    requireNotNull(row.get("title", String::class.java)),
                    requireNotNull(row.get("status", String::class.java)),
                    requireNotNull(row.get("submitted_at", OffsetDateTime::class.java)),
                    requireNotNull(row.get("version", Long::class.java)),
                    requireNotNull(row.get("lines", Long::class.java)),
                    requireNotNull(row.get("amount", BigDecimal::class.java)),
                    requireNotNull(row.get("submission_amount", BigDecimal::class.java)),
                )
            }
    }
}
