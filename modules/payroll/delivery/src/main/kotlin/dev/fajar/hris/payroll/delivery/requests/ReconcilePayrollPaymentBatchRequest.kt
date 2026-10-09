package dev.fajar.hris.payroll.delivery.requests

data class ReconcilePayrollPaymentBatchRequest(
    val expectedVersion: Long,
    val results: List<PayrollPaymentResultRequest>,
    val reason: String,
)
