package dev.fajar.hris.expenses.domain.entities

data class ExpensePaymentDestination(
    val bankCode: String,
    val accountNumber: String,
    val accountName: String,
)
