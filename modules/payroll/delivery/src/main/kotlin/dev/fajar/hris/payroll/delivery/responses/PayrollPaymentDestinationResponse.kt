package dev.fajar.hris.payroll.delivery.responses

data class PayrollPaymentDestinationResponse(
    val bankCode: String,
    val accountNumber: String,
    val accountName: String,
)
