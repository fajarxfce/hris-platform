package dev.fajar.hris.expenses.delivery.requests

data class ReconcileExpensePaymentBatchRequest(
    val expectedVersion: Long,
    val results: List<ExpensePaymentResultRequest>,
    val reason: String,
)
