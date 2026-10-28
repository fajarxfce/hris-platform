package dev.fajar.hris.reporting.domain.entities

data class LeaveReportAggregate(
    val totals: LeaveReportCounts,
    val statuses: List<LeaveStatusCounts>,
)
