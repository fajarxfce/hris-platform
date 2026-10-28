package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

class PostgresAttendanceClosingReportDataSource(private val sql: DSLContext) :
    AttendanceClosingReportDataSource {
    override fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceClosingReportRow> =
        sql.fetch(
                """
                with published as materialized (
                  select id as period_id,company_id,month,job_id,version,closed_at,timezone,
                    extract(day from month+interval '1 month'-interval '1 day')::integer month_days
                  from work_periods
                  where company_id=? and month between ? and ? and status='CLOSED'
                ), selected as (
                  select p.*,s.employment_id,s.payload original,coalesce(a.payload,s.payload) current,
                    coalesce(a.revision,0) adjustment_revision
                  from published p
                    left join work_period_snapshots s on s.company_id=p.company_id and s.job_id=p.job_id
                    left join lateral (
                      select payload,revision from attendance_adjustments a
                      where a.company_id=p.company_id and a.job_id=p.job_id and a.employment_id=s.employment_id
                      order by revision desc limit 1
                    ) a on true
                ), shaped as (
                  select s.*,
                    (case when jsonb_typeof(original->'days')='array' then jsonb_array_length(original->'days') else -1 end=month_days
                      and case when jsonb_typeof(current->'days')='array' then jsonb_array_length(current->'days') else -1 end=month_days) complete
                  from selected s
                ), projected as (
                  select s.*,d.* from shaped s
                  cross join lateral (
                    select count(*) days,
                      count(*) filter(where o->'schedule'->>'kind'='WORK') scheduled_days,
                      coalesce(sum(case when o->'schedule'->>'kind'='WORK' then (o->'schedule'->>'plannedMinutes')::bigint else 0 end),0) scheduled_minutes,
                      count(*) filter(where o->>'fact'='WORKED') original_worked,
                      count(*) filter(where o->>'fact'='ABSENCE_RECORDED') original_absence,
                      count(*) filter(where o->>'fact'='UNRECORDED') original_unrecorded,
                      count(*) filter(where o->>'fact'='OFF') original_off,
                      count(*) filter(where o->>'fact'='UNASSIGNED') original_unassigned,
                      coalesce(sum((o->>'acceptedMinutes')::bigint),0) original_minutes,
                      count(*) filter(where c->>'fact'='WORKED') current_worked,
                      count(*) filter(where c->>'fact'='ABSENCE_RECORDED') current_absence,
                      count(*) filter(where c->>'fact'='UNRECORDED') current_unrecorded,
                      count(*) filter(where c->>'fact'='OFF') current_off,
                      count(*) filter(where c->>'fact'='UNASSIGNED') current_unassigned,
                      coalesce(sum((c->>'acceptedMinutes')::bigint),0) current_minutes,
                      count(*) filter(where not coalesce(
                        o->>'workDate'=(s.month+ordinal::integer-1)::text and c->>'workDate'=o->>'workDate'
                        and o->>'fact' in ('WORKED','ABSENCE_RECORDED','UNRECORDED','OFF','UNASSIGNED')
                        and c->>'fact' in ('WORKED','ABSENCE_RECORDED','UNRECORDED','OFF','UNASSIGNED')
                        and (o->>'acceptedMinutes')::bigint between 0 and case when o->>'fact'='WORKED' then 1440 else 0 end
                        and (c->>'acceptedMinutes')::bigint between 0 and case when c->>'fact'='WORKED' then 1440 else 0 end
                        and o->'schedule'->>'workDate'=o->>'workDate'
                        and o->'schedule'->>'kind' in ('WORK','OFF','UNASSIGNED')
                        and (o->'schedule'->>'kind'<>'WORK' or (o->'schedule'->>'plannedMinutes')::bigint between 1 and 3600)
                        and o->'schedule'=c->'schedule'
                        and coalesce(o->'overtime','[]'::jsonb)=coalesce(c->'overtime','[]'::jsonb),false)) invalid_days
                    from jsonb_array_elements(case when s.complete then s.original->'days' else '[]'::jsonb end) with ordinality day(o,ordinal)
                    cross join lateral (select s.current->'days'->(ordinal::integer-1) c) latest
                  ) d
                )
                select month,period_id,job_id,version,closed_at,timezone,
                  count(distinct period_id) closed_months,count(employment_id) employment_months,
                  count(distinct employment_id) employments,
                  count(*) filter(where employment_id is not null and adjustment_revision>0) adjusted_employment_months,
                  coalesce(sum(days),0) days,coalesce(sum(scheduled_days),0) scheduled_days,coalesce(sum(scheduled_minutes),0) scheduled_minutes,
                  coalesce(sum(original_worked),0) original_worked,coalesce(sum(original_absence),0) original_absence,
                  coalesce(sum(original_unrecorded),0) original_unrecorded,coalesce(sum(original_off),0) original_off,
                  coalesce(sum(original_unassigned),0) original_unassigned,coalesce(sum(original_minutes),0) original_minutes,
                  coalesce(sum(current_worked),0) current_worked,coalesce(sum(current_absence),0) current_absence,
                  coalesce(sum(current_unrecorded),0) current_unrecorded,coalesce(sum(current_off),0) current_off,
                  coalesce(sum(current_unassigned),0) current_unassigned,coalesce(sum(current_minutes),0) current_minutes,
                  coalesce(sum(case when employment_id is null then 0 else invalid_days+
                    case when coalesce(complete and original->>'employeeId'=employment_id::text and current->>'employeeId'=employment_id::text
                      and original->>'month'=to_char(month,'YYYY-MM') and current->>'month'=to_char(month,'YYYY-MM'),false) then 0 else 1 end end),0) invalid_sources
                from projected group by grouping sets ((),(month,period_id,job_id,version,closed_at,timezone))
                """
                    .trimIndent(),
                company,
                from,
                until,
            )
            .map { row ->
                AttendanceClosingReportRow(
                    row.get("month", LocalDate::class.java),
                    row.get("period_id", UUID::class.java),
                    row.get("job_id", UUID::class.java),
                    row.get("version", Long::class.javaObjectType),
                    row.get("closed_at", OffsetDateTime::class.java),
                    row.get("timezone", String::class.java),
                    AttendanceClosingCountsRow(
                        row.number("closed_months"),
                        row.number("employment_months"),
                        row.number("employments"),
                        row.number("adjusted_employment_months"),
                        row.number("days"),
                        row.number("scheduled_days"),
                        row.number("scheduled_minutes"),
                        row.facts("original"),
                        row.facts("current"),
                    ),
                    row.number("invalid_sources"),
                )
            }

    private fun Record.number(name: String): Long = requireNotNull(get(name, Long::class.java))

    private fun Record.facts(prefix: String) =
        AttendanceClosingFactsRow(
            number("${prefix}_worked"),
            number("${prefix}_absence"),
            number("${prefix}_unrecorded"),
            number("${prefix}_off"),
            number("${prefix}_unassigned"),
            number("${prefix}_minutes"),
        )
}
