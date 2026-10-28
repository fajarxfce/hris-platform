package dev.fajar.hris.reporting.domain.entities

data class OvertimeReportCounts(
    val requests: Long = 0,
    val employments: Long = 0,
    val actualRequests: Long = 0,
    val requestedMinutes: Long = 0,
    val actualMinutes: Long = 0,
    val approvedMinutes: Long = 0,
)
