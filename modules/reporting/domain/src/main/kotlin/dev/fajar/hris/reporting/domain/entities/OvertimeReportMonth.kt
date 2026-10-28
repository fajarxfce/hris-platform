package dev.fajar.hris.reporting.domain.entities

import java.time.LocalDate
import java.time.YearMonth

data class OvertimeReportMonth(
    val month: YearMonth,
    val from: LocalDate,
    val until: LocalDate,
    val counts: OvertimeReportCounts,
)
