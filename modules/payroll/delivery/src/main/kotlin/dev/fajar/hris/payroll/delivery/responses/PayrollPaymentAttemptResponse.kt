package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollPaymentAttemptResponse(
    val batchId: UUID,
    val itemId: UUID,
    val status: String,
    val createdAt: Instant,
    val releasedAt: Instant?,
    val resolvedAt: Instant?,
)
