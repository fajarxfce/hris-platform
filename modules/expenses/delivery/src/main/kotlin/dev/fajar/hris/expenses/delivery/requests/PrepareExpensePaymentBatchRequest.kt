package dev.fajar.hris.expenses.delivery.requests

data class PrepareExpensePaymentBatchRequest(
    val title: String,
    val items: List<ExpensePaymentInstructionRequest>,
    val reason: String,
)
