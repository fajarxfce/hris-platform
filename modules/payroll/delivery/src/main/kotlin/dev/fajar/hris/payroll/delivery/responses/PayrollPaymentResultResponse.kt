package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollPaymentResultResponse(
    val itemId: UUID,
    val status: String,
    val transactionReference: String?,
    val occurredAt: Instant,
    val reason: String,
)
