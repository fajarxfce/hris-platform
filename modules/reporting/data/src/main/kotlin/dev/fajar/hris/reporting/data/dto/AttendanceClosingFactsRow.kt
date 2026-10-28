package dev.fajar.hris.reporting.data.dto

data class AttendanceClosingFactsRow(
    val workedDays: Long = 0,
    val absenceDays: Long = 0,
    val unrecordedDays: Long = 0,
    val offDays: Long = 0,
    val unassignedDays: Long = 0,
    val acceptedMinutes: Long = 0,
)
