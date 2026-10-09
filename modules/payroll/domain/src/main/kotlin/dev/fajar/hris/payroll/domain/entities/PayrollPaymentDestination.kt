package dev.fajar.hris.payroll.domain.entities

data class PayrollPaymentDestination(
    val bankCode: String,
    val accountNumber: String,
    val accountName: String,
)
