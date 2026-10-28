package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.AttendanceClosingReportAggregate
import java.time.YearMonth
import java.util.UUID

interface AttendanceClosingReportRepository {
    fun aggregate(
        company: UUID,
        from: YearMonth,
        until: YearMonth,
    ): Result<AttendanceClosingReportAggregate>
}
