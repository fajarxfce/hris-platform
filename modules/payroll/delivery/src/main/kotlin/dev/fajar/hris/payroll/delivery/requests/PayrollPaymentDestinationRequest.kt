package dev.fajar.hris.payroll.delivery.requests

data class PayrollPaymentDestinationRequest(
    val bankCode: String,
    val accountNumber: String,
    val accountName: String,
)
