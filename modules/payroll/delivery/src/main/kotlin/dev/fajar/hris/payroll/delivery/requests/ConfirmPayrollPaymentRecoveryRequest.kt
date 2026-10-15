package dev.fajar.hris.payroll.delivery.requests

import java.math.BigDecimal
import java.time.Instant

data class ConfirmPayrollPaymentRecoveryRequest(
    val assessmentRevision: Long,
    val paymentVersion: Long,
    val amount: BigDecimal,
    val transactionReference: String,
    val occurredAt: Instant,
    val reason: String,
)
