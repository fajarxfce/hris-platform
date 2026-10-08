package dev.fajar.hris.expenses.delivery.requests

data class ExpensePaymentDestinationRequest(
    val bankCode: String,
    val accountNumber: String,
    val accountName: String,
)
