package dev.fajar.hris.reporting.domain.entities

data class ExpenseReportAggregate(
    val totals: ExpenseReportCounts,
    val statuses: List<ExpenseClaimStatusCounts>,
)
