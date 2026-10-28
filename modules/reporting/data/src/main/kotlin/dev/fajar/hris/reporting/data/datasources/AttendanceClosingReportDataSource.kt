package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.AttendanceClosingEmployeeRow
import dev.fajar.hris.reporting.data.dto.AttendanceClosingReportRow
import java.time.LocalDate
import java.util.UUID

interface AttendanceClosingReportDataSource {
    fun employees(
        company: UUID,
        month: LocalDate,
        after: UUID?,
        limit: Int,
    ): List<AttendanceClosingEmployeeRow>

    fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceClosingReportRow>
}
