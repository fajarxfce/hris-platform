package dev.fajar.hris.reporting.domain.entities

data class AttendanceClosingFacts(
    val workedDays: Long = 0,
    val absenceDays: Long = 0,
    val unrecordedDays: Long = 0,
    val offDays: Long = 0,
    val unassignedDays: Long = 0,
    val acceptedMinutes: Long = 0,
)
