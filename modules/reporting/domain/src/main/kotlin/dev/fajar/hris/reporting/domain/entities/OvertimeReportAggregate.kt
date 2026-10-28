package dev.fajar.hris.reporting.domain.entities

data class OvertimeReportAggregate(
    val totals: OvertimeReportCounts,
    val months: List<OvertimeReportMonth>,
)
