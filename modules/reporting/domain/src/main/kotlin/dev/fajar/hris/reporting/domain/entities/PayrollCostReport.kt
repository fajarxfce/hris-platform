package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class PayrollCostReport(
    val companyId: UUID,
    val from: YearMonth,
    val until: YearMonth,
    val evaluatedAt: Instant,
    val aggregate: PayrollCostReportAggregate,
) {
    val definitionVersion: String = "payroll-costs.v1"
    val currency: String = "IDR"
}
