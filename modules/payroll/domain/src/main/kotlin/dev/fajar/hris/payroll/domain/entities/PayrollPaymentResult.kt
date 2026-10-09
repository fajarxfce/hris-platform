package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollPaymentResult(
    val itemId: UUID,
    val status: PayrollPaymentItemStatus,
    val transactionReference: String?,
    val occurredAt: Instant,
    val reason: String,
    val confirmedNoTransfer: Boolean = false,
)
