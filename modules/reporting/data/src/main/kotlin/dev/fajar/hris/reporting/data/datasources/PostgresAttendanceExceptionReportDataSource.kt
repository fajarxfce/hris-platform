package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.AttendanceExceptionAggregateRow
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresAttendanceExceptionReportDataSource(private val sql: DSLContext) :
    AttendanceExceptionReportDataSource {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceExceptionAggregateRow> =
        sql.fetch(
                """
                with event_days as (
                    select e.company_id, e.employment_id, e.work_date,
                        count(*) filter (where e.initial_status='PENDING' and r.event_id is null) as pending,
                        count(*) filter (where e.kind='CLOCK_IN' and coalesce(r.decision='ACCEPT',e.initial_status='ACCEPTED')) as accepted_in,
                        count(*) filter (where e.kind='CLOCK_OUT' and coalesce(r.decision='ACCEPT',e.initial_status='ACCEPTED')) as accepted_out
                    from attendance_events e
                    left join attendance_reviews r on r.company_id=e.company_id and r.event_id=e.id
                    where e.company_id=? and e.work_date between ? and ?
                    group by e.company_id,e.employment_id,e.work_date
                ), classified as (
                    select d.*, ((d.accepted_in=1) <> (d.accepted_out=1)) and not exists (
                        select 1 from attendance_corrections c where c.company_id=d.company_id
                            and c.employment_id=d.employment_id and c.work_date=d.work_date
                    ) as incomplete
                    from event_days d
                )
                select work_date,
                    count(*) as observed_days,
                    count(*) filter (where pending>0 or incomplete) as affected_days,
                    count(distinct employment_id) filter (where pending>0 or incomplete) as affected_employments,
                    count(*) filter (where pending>0) as pending_days,
                    count(*) filter (where incomplete) as incomplete_days,
                    coalesce(sum(pending),0)::bigint as pending_events
                from classified group by grouping sets ((),(work_date))
                """
                    .trimIndent(),
                company,
                from,
                until,
            )
            .map { row ->
                AttendanceExceptionAggregateRow(
                    row.get("work_date", LocalDate::class.java),
                    requireNotNull(row.get("observed_days", Long::class.java)),
                    requireNotNull(row.get("affected_days", Long::class.java)),
                    requireNotNull(row.get("affected_employments", Long::class.java)),
                    requireNotNull(row.get("pending_days", Long::class.java)),
                    requireNotNull(row.get("incomplete_days", Long::class.java)),
                    requireNotNull(row.get("pending_events", Long::class.java)),
                )
            }
}
