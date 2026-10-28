package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.reporting.data.datasources.AttendanceExceptionReportDataSource
import dev.fajar.hris.reporting.data.mappers.toAttendanceExceptionAggregate
import dev.fajar.hris.reporting.domain.repositories.AttendanceExceptionReportRepository
import java.time.LocalDate
import java.util.UUID

class StoredAttendanceExceptionReportRepository(
    private val source: AttendanceExceptionReportDataSource
) : AttendanceExceptionReportRepository {
    override fun aggregate(company: UUID, from: LocalDate, until: LocalDate) = safeDatabaseCall {
        source.aggregate(company, from, until).toAttendanceExceptionAggregate(from, until)
    }
}
