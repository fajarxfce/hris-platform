package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.AttendanceExceptionAggregateRow
import java.time.LocalDate
import java.util.UUID

interface AttendanceExceptionReportDataSource {
    fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceExceptionAggregateRow>
}
