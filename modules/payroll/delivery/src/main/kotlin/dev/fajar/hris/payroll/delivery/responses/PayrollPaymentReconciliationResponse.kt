package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollPaymentReconciliationResponse(
    val batchVersion: Long,
    val actorId: UUID,
    val recordedAt: Instant,
    val resultingStatus: String,
    val results: List<PayrollPaymentResultResponse>,
)
