package dev.fajar.hris.reporting.data.dto

import java.time.LocalDate

data class OvertimeReportAggregateRow(
    val month: LocalDate?,
    val requests: Long,
    val employments: Long,
    val actualRequests: Long,
    val requestedMinutes: Long,
    val actualMinutes: Long,
    val approvedMinutes: Long,
)
