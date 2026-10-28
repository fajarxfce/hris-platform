package dev.fajar.hris.reporting.delivery.responses

data class OvertimeReportCountsResponse(
    val requests: Long,
    val employments: Long,
    val actualRequests: Long,
    val requestedMinutes: Long,
    val actualMinutes: Long,
    val approvedMinutes: Long,
)
