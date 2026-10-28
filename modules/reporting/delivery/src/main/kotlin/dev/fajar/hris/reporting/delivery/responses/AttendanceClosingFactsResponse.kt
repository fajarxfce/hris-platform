package dev.fajar.hris.reporting.delivery.responses

data class AttendanceClosingFactsResponse(
    val workedDays: Long,
    val absenceDays: Long,
    val unrecordedDays: Long,
    val offDays: Long,
    val unassignedDays: Long,
    val acceptedMinutes: Long,
)
