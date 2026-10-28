package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import org.jooq.DSLContext

class PostgresReportSnapshotSourceDataSource(private val sql: DSLContext) :
    ReportSnapshotSourceDataSource {
    override fun captureLeave(query: ReportDetailCaptureQuery) =
        capture(
            query,
            """$matchingLeaveReportRequestsSql,
        export_source as materialized (
            select row_number() over(order by submitted_at desc,id desc)::integer ordinal,
                ?::jsonb || jsonb_build_array(id::text,employment_id::text,employee_number,employee_name,type_code,type_name,status,
                    ${utc("submitted_at")},version::text,window_half_days::text,(window_half_days::numeric*0.5)::text,
                    request_half_days::text,(request_half_days::numeric*0.5)::text) cells,
                case when invalid then 1 else 0 end invalid_sources
            from matching_requests order by submitted_at desc,id desc limit ?
        )""",
            listOf(query.company, query.until, query.from, query.from, query.until),
        )

    override fun captureExpenses(query: ReportDetailCaptureQuery) =
        capture(
            query,
            """$matchingExpenseReportClaimsSql,
        export_source as materialized (
            select row_number() over(order by submitted_at desc,id desc)::integer ordinal,
                ?::jsonb || jsonb_build_array(id::text,submission_id::text,submission_number::text,employment_id::text,
                    employee_number,employee_name,title,status,${utc("submitted_at")},version::text,lines::text,amount::text,submission_amount::text) cells,
                0 invalid_sources
            from matching_claims order by submitted_at desc,id desc limit ?
        )""",
            listOf(query.company, query.from, query.until),
        )

    override fun captureClosings(query: ReportDetailCaptureQuery) =
        capture(
            query,
            """${attendanceClosingProjectionSql(attendanceClosingSnapshotsSql(false))},
        export_source as materialized (
            select row_number() over(order by p.month,p.employment_id)::integer ordinal,
                ?::jsonb || jsonb_build_array(to_char(p.month,'YYYY-MM'),p.employment_id::text,e.employee_number,n.legal_name,
                    p.period_id::text,p.job_id::text,p.version::text,${utc("p.closed_at")},p.timezone,
                    p.adjustment_id::text,case when p.adjustment_revision>0 then p.adjustment_revision::text end,${utc("p.adjustment_at")},
                    1::text,1::text,1::text,(case when p.adjustment_revision>0 then 1 else 0 end)::text,
                    p.days::text,p.scheduled_days::text,p.scheduled_minutes::text,
                    p.original_worked::text,p.original_absence::text,p.original_unrecorded::text,p.original_off::text,p.original_unassigned::text,p.original_minutes::text,
                    p.current_worked::text,p.current_absence::text,p.current_unrecorded::text,p.current_off::text,p.current_unassigned::text,p.current_minutes::text) cells,
                p.invalid_days+case when coalesce(p.complete and p.original->>'employeeId'=p.employment_id::text
                    and p.current->>'employeeId'=p.employment_id::text and p.original->>'month'=to_char(p.month,'YYYY-MM')
                    and p.current->>'month'=to_char(p.month,'YYYY-MM'),false) then 0 else 1 end invalid_sources
            from projected p
            left join employments e on e.company_id=p.company_id and e.id=p.employment_id
            left join persons n on n.id=e.person_id
            where p.employment_id is not null order by p.month,p.employment_id limit ?
        )""",
            listOf(query.company, query.from, query.until),
        )

    private fun capture(
        query: ReportDetailCaptureQuery,
        projection: String,
        bindings: List<Any>,
    ): ReportCaptureTotalsRow {
        val values =
            bindings +
                listOf(
                    query.prefix,
                    query.maximumRows + 1,
                    query.company,
                    query.account,
                    query.snapshot,
                    query.maximumRows,
                    query.maximumBytes,
                )
        val row =
            requireNotNull(
                sql.fetchOne(
                    """$projection,
            export_stats as (
                select count(*)::integer row_count,coalesce(sum(octet_length(cells::text)),0)::bigint stored_bytes,
                    coalesce(sum(invalid_sources),0)::bigint invalid_sources from export_source
            ), stored as (
                insert into report_snapshot_rows(company_id,account_id,snapshot_id,row_number,cells)
                select ?,?,?,s.ordinal,s.cells from export_source s cross join export_stats t
                where t.row_count<=? and t.stored_bytes<=? and t.invalid_sources=0 returning row_number
            ) select t.row_count,t.stored_bytes,t.invalid_sources,(select count(*)::integer from stored) inserted from export_stats t""",
                    *values.toTypedArray(),
                )
            )
        return ReportCaptureTotalsRow(
            row.get("row_count", Int::class.java),
            row.get("stored_bytes", Long::class.java),
            row.get("invalid_sources", Long::class.java),
            row.get("inserted", Int::class.java),
        )
    }

    private fun utc(column: String) =
        "to_char($column at time zone 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"
}
