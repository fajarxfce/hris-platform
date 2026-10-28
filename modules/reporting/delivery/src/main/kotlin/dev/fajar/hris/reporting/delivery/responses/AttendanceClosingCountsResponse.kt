package dev.fajar.hris.reporting.delivery.responses

data class AttendanceClosingCountsResponse(
    val closedMonths: Long,
    val employmentMonths: Long,
    val employments: Long,
    val adjustedEmploymentMonths: Long,
    val days: Long,
    val scheduledDays: Long,
    val scheduledMinutes: Long,
    val original: AttendanceClosingFactsResponse,
    val current: AttendanceClosingFactsResponse,
)
