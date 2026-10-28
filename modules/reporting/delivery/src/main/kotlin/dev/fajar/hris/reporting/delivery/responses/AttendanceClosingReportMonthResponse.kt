package dev.fajar.hris.reporting.delivery.responses

import java.time.YearMonth

data class AttendanceClosingReportMonthResponse(
    val month: YearMonth,
    val closing: AttendanceClosingReferenceResponse?,
    val counts: AttendanceClosingCountsResponse,
)
