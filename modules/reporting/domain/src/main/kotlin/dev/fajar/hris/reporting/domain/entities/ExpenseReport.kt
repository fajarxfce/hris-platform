package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExpenseReport(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val evaluatedAt: Instant,
    val aggregate: ExpenseReportAggregate,
) {
    val definitionVersion = "expenses.v1"
}
