package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.PayrollCostReportAggregateRow
import java.time.LocalDate
import java.util.UUID

interface PayrollCostReportDataSource {
    fun aggregate(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<PayrollCostReportAggregateRow>
}
