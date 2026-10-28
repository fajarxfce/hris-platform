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
                ${attendanceClosingProjectionSql(attendanceClosingSnapshotsSql(false))}
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
            .map { it.toReportRow() }

    override fun employees(
        company: UUID,
        month: LocalDate,
        after: UUID?,
        limit: Int,
    ): List<AttendanceClosingEmployeeRow> =
        sql.fetch(
                """
            ${attendanceClosingProjectionSql(attendanceClosingSnapshotsSql(true))}
            select p.month,p.period_id,p.job_id,p.version,p.closed_at,p.timezone,p.employment_id,
                e.employee_number,n.legal_name employee_name,p.adjustment_id,p.adjustment_revision,p.adjustment_at,
                1 closed_months,1 employment_months,1 employments,
                case when adjustment_revision>0 then 1 else 0 end adjusted_employment_months,
                days,scheduled_days,scheduled_minutes,
                original_worked,original_absence,original_unrecorded,original_off,original_unassigned,original_minutes,
                current_worked,current_absence,current_unrecorded,current_off,current_unassigned,current_minutes,
                invalid_days+case when coalesce(complete and original->>'employeeId'=employment_id::text
                    and current->>'employeeId'=employment_id::text and original->>'month'=to_char(month,'YYYY-MM')
                    and current->>'month'=to_char(month,'YYYY-MM'),false) then 0 else 1 end invalid_sources
            from projected p
            left join employments e on e.company_id=p.company_id and e.id=p.employment_id
            left join persons n on n.id=e.person_id
            where p.employment_id is not null order by p.employment_id
            """
                    .trimIndent(),
                company,
                month,
                month,
                after,
                after,
                limit,
            )
            .map { row ->
                AttendanceClosingEmployeeRow(
                    requireNotNull(row.get("employment_id", UUID::class.java)),
                    requireNotNull(row.get("employee_number", String::class.java)),
                    requireNotNull(row.get("employee_name", String::class.java)),
                    row.get("adjustment_id", UUID::class.java),
                    row.number("adjustment_revision"),
                    row.get("adjustment_at", OffsetDateTime::class.java),
                    row.toReportRow(),
                )
            }

    private fun Record.toReportRow() =
        AttendanceClosingReportRow(
            get("month", LocalDate::class.java),
            get("period_id", UUID::class.java),
            get("job_id", UUID::class.java),
            get("version", Long::class.javaObjectType),
            get("closed_at", OffsetDateTime::class.java),
            get("timezone", String::class.java),
            AttendanceClosingCountsRow(
                number("closed_months"),
                number("employment_months"),
                number("employments"),
                number("adjusted_employment_months"),
                number("days"),
                number("scheduled_days"),
                number("scheduled_minutes"),
                facts("original"),
                facts("current"),
            ),
            number("invalid_sources"),
        )

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
