package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.reporting.data.datasources.AttendanceClosingReportDataSource
import dev.fajar.hris.reporting.data.mappers.toAttendanceClosingReportAggregate
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import java.time.YearMonth
import java.util.UUID

class StoredAttendanceClosingReportRepository(
    private val source: AttendanceClosingReportDataSource
) : AttendanceClosingReportRepository {
    override fun aggregate(company: UUID, from: YearMonth, until: YearMonth) = safeDatabaseCall {
        source
            .aggregate(company, from.atDay(1), until.atDay(1))
            .toAttendanceClosingReportAggregate(from, until)
    }
}
