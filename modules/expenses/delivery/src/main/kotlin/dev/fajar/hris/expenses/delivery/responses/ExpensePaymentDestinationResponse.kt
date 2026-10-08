package dev.fajar.hris.expenses.delivery.responses

data class ExpensePaymentDestinationResponse(
    val bankCode: String,
    val accountNumber: String,
    val accountName: String,
)
