package dev.fajar.hris.reporting.domain.entities

data class AttendanceExceptionAggregate(
    val totals: AttendanceExceptionCounts,
    val days: List<AttendanceExceptionDay>,
)
