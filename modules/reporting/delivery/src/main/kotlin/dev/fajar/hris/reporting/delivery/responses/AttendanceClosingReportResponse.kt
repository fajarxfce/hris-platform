package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class AttendanceClosingReportResponse(
    val companyId: UUID,
    val from: YearMonth,
    val until: YearMonth,
    val evaluatedAt: Instant,
    val definitionVersion: String,
    val totals: AttendanceClosingCountsResponse,
    val months: List<AttendanceClosingReportMonthResponse>,
)
