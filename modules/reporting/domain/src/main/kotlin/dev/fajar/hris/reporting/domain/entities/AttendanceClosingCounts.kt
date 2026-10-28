package dev.fajar.hris.reporting.domain.entities

data class AttendanceClosingCounts(
    val closedMonths: Long = 0,
    val employmentMonths: Long = 0,
    val employments: Long = 0,
    val adjustedEmploymentMonths: Long = 0,
    val days: Long = 0,
    val scheduledDays: Long = 0,
    val scheduledMinutes: Long = 0,
    val original: AttendanceClosingFacts = AttendanceClosingFacts(),
    val current: AttendanceClosingFacts = AttendanceClosingFacts(),
)
