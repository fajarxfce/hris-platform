package dev.fajar.hris.reporting.data.datasources

// The immutable initial revision fixes whether an employment existed by month end.
// Closing targets may also include future hires; those snapshots are not historical results.
internal fun attendanceClosingSnapshotsSql(paged: Boolean) =
    """
    lateral (
      select w.company_id,w.job_id,w.employment_id,w.payload from work_period_snapshots w
      where w.company_id=p.company_id and w.job_id=p.job_id
        and exists(select 1 from employment_revisions r
          where r.company_id=w.company_id and r.employment_id=w.employment_id and r.revision=0
            and r.effective_from<=(p.month+interval '1 month'-interval '1 day')::date)
        ${if (paged) "and (?::uuid is null or w.employment_id>?::uuid)" else ""}
      ${if (paged) "order by w.employment_id limit ?" else ""}
    )
"""
        .trimIndent()

internal fun attendanceClosingProjectionSql(snapshots: String) =
    """
            with published as materialized (
              select id as period_id,company_id,month,job_id,version,closed_at,timezone,
                extract(day from month+interval '1 month'-interval '1 day')::integer month_days
              from work_periods
              where company_id=? and month between ? and ? and status='CLOSED'
            ), selected as (
              select p.*,s.employment_id,s.payload original,coalesce(a.payload,s.payload) current,
                coalesce(a.revision,0) adjustment_revision,a.id adjustment_id,a.recorded_at adjustment_at
              from published p
                left join $snapshots s on s.company_id=p.company_id and s.job_id=p.job_id
                left join lateral (
                  select id,payload,revision,recorded_at from attendance_adjustments a
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
"""
        .trimIndent()
