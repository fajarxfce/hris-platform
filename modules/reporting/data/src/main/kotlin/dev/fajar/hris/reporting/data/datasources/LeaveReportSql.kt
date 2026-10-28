package dev.fajar.hris.reporting.data.datasources

// Immutable request dates define both the aggregate and its drill-down, including released
// allocations.
internal val matchingLeaveReportRequestsSql =
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
