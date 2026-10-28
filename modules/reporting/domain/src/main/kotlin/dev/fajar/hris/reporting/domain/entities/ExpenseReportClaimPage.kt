package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExpenseReportClaimPage(
    val companyId: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val status: ExpenseClaimStatus?,
    val evaluatedAt: Instant,
    val page: Page<ExpenseReportClaim>,
) {
    val definitionVersion = "expenses.v1"
}
