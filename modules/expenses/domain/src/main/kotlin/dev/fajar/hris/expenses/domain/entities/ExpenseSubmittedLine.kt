package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ExpenseSubmittedLine(
    val id: UUID,
    val occurredOn: LocalDate,
    val amount: BigDecimal,
    val description: String,
    val category: ExpenseCategory,
    val costCenter: ExpenseCostCenterSnapshot?,
    val receipts: List<ExpenseSubmittedReceipt>,
)
