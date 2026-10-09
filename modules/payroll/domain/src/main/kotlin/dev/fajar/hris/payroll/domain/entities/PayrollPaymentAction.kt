package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollPaymentAction(
    val batchId: UUID,
    val version: Long,
    val kind: PayrollPaymentActionKind,
    val status: PayrollPaymentBatchStatus,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
