package dev.fajar.hris.reporting.domain.entities

data class LeaveReportCounts(
    val requests: Long = 0,
    val employments: Long = 0,
    val halfDays: Long = 0,
)
