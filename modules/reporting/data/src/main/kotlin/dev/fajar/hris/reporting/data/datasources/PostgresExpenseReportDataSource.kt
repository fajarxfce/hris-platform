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
            $matchingClaims
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
            $matchingClaims
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

    private companion object {
        // Only the latest submitted revision contributes; subsequent unsent draft edits cannot
        // replace its evidence.
        val matchingClaims =
            """
            with matching_claims as (
                select c.id,c.employment_id,c.status,c.version,s.id as submission_id,s.number as submission_number,
                    s.submitted_at,s.employee_number,s.employee_name,d.title,d.total_amount as submission_amount,
                    count(*) as lines,sum(l.amount) as amount
                from expense_draft_lines l
                join expense_submitted_lines sl on sl.company_id=l.company_id and sl.claim_id=l.claim_id
                    and sl.draft_revision=l.draft_revision and sl.line_id=l.id
                join expense_submissions s on s.company_id=sl.company_id and s.id=sl.submission_id
                    and s.claim_id=sl.claim_id and s.draft_revision=sl.draft_revision
                join expense_claims c on c.company_id=s.company_id and c.id=s.claim_id and c.latest_submission_id=s.id
                join expense_drafts d on d.company_id=s.company_id and d.claim_id=s.claim_id and d.revision=s.draft_revision
                where l.company_id=? and l.occurred_on between ? and ?
                group by c.id,c.employment_id,c.status,c.version,s.id,s.number,s.submitted_at,s.employee_number,s.employee_name,d.title,d.total_amount
            )
            """
                .trimIndent()
    }
}
