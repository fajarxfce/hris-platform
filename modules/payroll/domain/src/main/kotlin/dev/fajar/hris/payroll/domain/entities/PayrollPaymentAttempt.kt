package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollPaymentAttempt(
    val batchId: UUID,
    val itemId: UUID,
    val status: PayrollPaymentItemStatus,
    val createdAt: Instant,
    val releasedAt: Instant?,
    val resolvedAt: Instant?,
)
