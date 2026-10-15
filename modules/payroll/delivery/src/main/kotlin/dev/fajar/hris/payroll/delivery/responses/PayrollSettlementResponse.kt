package dev.fajar.hris.payroll.delivery.responses

data class PayrollSettlementResponse(
    val assessmentRevision: Long,
    val entitlement: String,
    val confirmedPaid: String,
    val confirmedRecovered: String,
    val payable: String,
    val recoverable: String,
    val currency: String,
)
