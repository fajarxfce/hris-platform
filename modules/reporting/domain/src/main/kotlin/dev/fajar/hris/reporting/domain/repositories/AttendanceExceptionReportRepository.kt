package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.AttendanceExceptionAggregate
import java.time.LocalDate
import java.util.UUID

interface AttendanceExceptionReportRepository {
    fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<AttendanceExceptionAggregate>
}
