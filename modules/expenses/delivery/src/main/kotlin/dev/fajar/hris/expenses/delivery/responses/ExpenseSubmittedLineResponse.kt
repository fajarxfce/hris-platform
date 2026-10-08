package dev.fajar.hris.expenses.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class ExpenseSubmittedLineResponse(
    val id: UUID,
    val occurredOn: LocalDate,
    val amount: String,
    val description: String,
    val category: ExpenseCategoryResponse,
    val costCenter: ExpenseCostCenterSnapshotResponse?,
    val receipts: List<ExpenseSubmittedReceiptResponse>,
)
