package dev.fajar.hris.reporting.domain.entities

data class PayrollCostReportAggregate(
    val totals: PayrollCostReportCounts,
    val months: List<PayrollCostReportMonth>,
)
