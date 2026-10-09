package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollPaymentReconciliation(
    val batchId: UUID,
    val batchVersion: Long,
    val actorId: UUID,
    val recordedAt: Instant,
    val resultingStatus: PayrollPaymentBatchStatus,
    val results: List<PayrollPaymentResult>,
)
