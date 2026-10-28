package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.OvertimeReportAggregateRow
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresOvertimeReportDataSource(private val sql: DSLContext) : OvertimeReportDataSource {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
    ): List<OvertimeReportAggregateRow> {
        val statusFilter = if (status == null) "" else "and status=?"
        val values = listOfNotNull<Any>(company, from, until, status).toTypedArray()
        return sql.fetch(
                """
            select date_trunc('month',work_date::timestamp)::date as month,
                count(*) as requests,count(distinct employment_id) as employments,
                count(actual_start) as actual_requests,
                coalesce(sum(extract(epoch from requested_end-requested_start)/60-requested_break),0)::bigint as requested_minutes,
                coalesce(sum(extract(epoch from actual_end-actual_start)/60-actual_break),0)::bigint as actual_minutes,
                coalesce(sum(approved_minutes),0)::bigint as approved_minutes
            from overtime_requests where company_id=? and work_date between ? and ? $statusFilter
            group by grouping sets ((),(date_trunc('month',work_date::timestamp)::date))
            """
                    .trimIndent(),
                *values,
            )
            .map { row ->
                OvertimeReportAggregateRow(
                    row.get("month", LocalDate::class.java),
                    requireNotNull(row.get("requests", Long::class.java)),
                    requireNotNull(row.get("employments", Long::class.java)),
                    requireNotNull(row.get("actual_requests", Long::class.java)),
                    requireNotNull(row.get("requested_minutes", Long::class.java)),
                    requireNotNull(row.get("actual_minutes", Long::class.java)),
                    requireNotNull(row.get("approved_minutes", Long::class.java)),
                )
            }
    }
}
