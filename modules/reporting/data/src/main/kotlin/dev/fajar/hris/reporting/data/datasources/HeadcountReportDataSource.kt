package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.HeadcountAggregateRow
import java.time.LocalDate
import java.util.UUID

interface HeadcountReportDataSource {
    fun count(
        companies: Set<UUID>,
        asOf: LocalDate,
        statuses: Set<String>,
    ): List<HeadcountAggregateRow>
}
