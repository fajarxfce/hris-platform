package dev.fajar.hris.reporting.data.dto

data class LeaveReportAggregateRow(
    val status: String?,
    val requests: Long,
    val employments: Long,
    val halfDays: Long,
    val invalidRequests: Long,
)
