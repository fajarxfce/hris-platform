package dev.fajar.hris.reporting.data.dto

data class AttendanceClosingCountsRow(
    val closedMonths: Long = 0,
    val employmentMonths: Long = 0,
    val employments: Long = 0,
    val adjustedEmploymentMonths: Long = 0,
    val days: Long = 0,
    val scheduledDays: Long = 0,
    val scheduledMinutes: Long = 0,
    val original: AttendanceClosingFactsRow = AttendanceClosingFactsRow(),
    val current: AttendanceClosingFactsRow = AttendanceClosingFactsRow(),
)
