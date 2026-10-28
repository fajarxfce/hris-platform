package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.OvertimeReportAggregateRow
import java.time.LocalDate
import java.util.UUID

interface OvertimeReportDataSource {
    fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
    ): List<OvertimeReportAggregateRow>
}
