package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.PayrollPaymentItemStatus
import java.time.Instant
import java.util.UUID

data class PayrollPaymentResultRequest(
    val itemId: UUID,
    val status: PayrollPaymentItemStatus,
    val transactionReference: String? = null,
    val occurredAt: Instant,
    val reason: String,
    val confirmedNoTransfer: Boolean = false,
)
