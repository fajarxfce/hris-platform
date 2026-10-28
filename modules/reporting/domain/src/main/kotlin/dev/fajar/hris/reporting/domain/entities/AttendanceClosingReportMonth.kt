package dev.fajar.hris.reporting.domain.entities

import java.time.YearMonth

data class AttendanceClosingReportMonth(
    val month: YearMonth,
    val closing: AttendanceClosingReference?,
    val counts: AttendanceClosingCounts,
)
