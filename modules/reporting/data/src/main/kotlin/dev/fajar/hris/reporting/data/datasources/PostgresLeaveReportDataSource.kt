package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresLeaveReportDataSource(private val sql: DSLContext) : LeaveReportDataSource {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<LeaveReportAggregateRow> =
        sql.fetch(
                """
            $matchingRequests
            select status, count(*) as requests, count(distinct employment_id) as employments,
                coalesce(sum(window_half_days),0)::bigint as half_days,
                count(*) filter (where invalid) as invalid_requests
            from matching_requests group by grouping sets ((),(status))
            """
                    .trimIndent(),
                company,
                until,
                from,
                from,
                until,
            )
            .map { row ->
                LeaveReportAggregateRow(
                    row.get("status", String::class.java),
                    requireNotNull(row.get("requests", Long::class.java)),
                    requireNotNull(row.get("employments", Long::class.java)),
                    requireNotNull(row.get("half_days", Long::class.java)),
                    requireNotNull(row.get("invalid_requests", Long::class.java)),
                )
            }

    override fun requests(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        afterAt: OffsetDateTime?,
        afterId: UUID?,
        limit: Int,
    ): List<LeaveReportRequestRow> {
        val filters = mutableListOf<String>()
        val values = mutableListOf<Any>(company, until, from, from, until)
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
            $matchingRequests
            select id,employment_id,employee_number,employee_name,type_code,type_name,status,
                submitted_at,version,window_half_days,request_half_days,invalid
            from matching_requests $where order by submitted_at desc,id desc limit ?
            """
                    .trimIndent(),
                *values.toTypedArray(),
            )
            .map { row ->
                LeaveReportRequestRow(
                    requireNotNull(row.get("id", UUID::class.java)),
                    requireNotNull(row.get("employment_id", UUID::class.java)),
                    requireNotNull(row.get("employee_number", String::class.java)),
                    requireNotNull(row.get("employee_name", String::class.java)),
                    requireNotNull(row.get("type_code", String::class.java)),
                    requireNotNull(row.get("type_name", String::class.java)),
                    requireNotNull(row.get("status", String::class.java)),
                    requireNotNull(row.get("submitted_at", OffsetDateTime::class.java)),
                    requireNotNull(row.get("version", Long::class.java)),
                    requireNotNull(row.get("window_half_days", Long::class.java)),
                    requireNotNull(row.get("request_half_days", Long::class.java)),
                    requireNotNull(row.get("invalid", Boolean::class.java)),
                )
            }
    }

    private companion object {
        // Immutable request dates define both the aggregate and its drill-down, including released
        // allocations.
        val matchingRequests =
            """
            with candidates as (
                select id,employment_id,employee_number,employee_name,type_code,type_name,status,
                    submitted_at,version,half_days as request_half_days,days
                from leave_requests where company_id=? and starts_on<=? and ends_on>=?
            ), matching_requests as (
                select r.id,r.employment_id,r.employee_number,r.employee_name,r.type_code,r.type_name,
                    r.status,r.submitted_at,r.version,r.request_half_days,d.window_half_days,
                    d.invalid or d.window_half_days>r.request_half_days as invalid
                from candidates r cross join lateral (
                    select sum(units)::bigint as window_half_days,
                        count(*)<>count(units) or count(*)<>count(distinct work_date) as invalid,
                        count(*) as matched_days
                    from (
                        select (day->>'workDate')::date as work_date,
                            case day->>'portion' when 'FULL' then 2 when 'FIRST_HALF' then 1
                                when 'SECOND_HALF' then 1 end as units
                        from jsonb_array_elements(r.days) day
                    ) days where work_date between ? and ?
                ) d where d.matched_days>0
            )
            """
                .trimIndent()
    }
}
