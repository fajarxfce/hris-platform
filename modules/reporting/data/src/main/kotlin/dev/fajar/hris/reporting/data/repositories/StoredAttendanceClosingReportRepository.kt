package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.reporting.data.datasources.AttendanceClosingReportDataSource
import dev.fajar.hris.reporting.data.mappers.toAttendanceClosingEmployee
import dev.fajar.hris.reporting.data.mappers.toAttendanceClosingReportAggregate
import dev.fajar.hris.reporting.domain.policies.attendanceClosingEmployeeCursor
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import java.time.YearMonth
import java.util.UUID

class StoredAttendanceClosingReportRepository(
    private val source: AttendanceClosingReportDataSource
) : AttendanceClosingReportRepository {
    override fun employees(company: UUID, month: YearMonth, after: UUID?, limit: Int) =
        safeDatabaseCall {
            check(month.year in 2000..2100 && limit in 1..50)
            val rows = source.employees(company, month.atDay(1), after, limit + 1)
            check(rows.size <= limit + 1)
            val values = rows.map { it.toAttendanceClosingEmployee(month) }
            check(
                values.zipWithNext().all { (a, b) ->
                    a.employeeId.toString() < b.employeeId.toString()
                }
            )
            check(after == null || values.all { it.employeeId.toString() > after.toString() })
            check(values.map { it.closing }.distinct().size <= 1)
            val items = java.util.List.copyOf(values.take(limit))
            Page(
                items,
                if (values.size > limit)
                    attendanceClosingEmployeeCursor(company, month, items.last().employeeId)
                else null,
            )
        }

    override fun aggregate(company: UUID, from: YearMonth, until: YearMonth) = safeDatabaseCall {
        source
            .aggregate(company, from.atDay(1), until.atDay(1))
            .toAttendanceClosingReportAggregate(from, until)
    }
}
