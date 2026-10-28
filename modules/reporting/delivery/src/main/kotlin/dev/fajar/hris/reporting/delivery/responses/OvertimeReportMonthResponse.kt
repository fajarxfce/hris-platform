package dev.fajar.hris.reporting.delivery.responses

import java.time.LocalDate
import java.time.YearMonth

data class OvertimeReportMonthResponse(
    val month: YearMonth,
    val from: LocalDate,
    val until: LocalDate,
    val counts: OvertimeReportCountsResponse,
)
