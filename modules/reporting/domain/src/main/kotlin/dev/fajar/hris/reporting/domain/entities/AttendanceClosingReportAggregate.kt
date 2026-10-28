package dev.fajar.hris.reporting.domain.entities

data class AttendanceClosingReportAggregate(
    val totals: AttendanceClosingCounts,
    val months: List<AttendanceClosingReportMonth>,
)
