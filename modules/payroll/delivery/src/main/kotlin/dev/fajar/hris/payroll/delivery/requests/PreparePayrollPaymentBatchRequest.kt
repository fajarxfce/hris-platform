package dev.fajar.hris.payroll.delivery.requests

data class PreparePayrollPaymentBatchRequest(
    val title: String,
    val items: List<PayrollPaymentInstructionRequest>,
    val reason: String,
)
