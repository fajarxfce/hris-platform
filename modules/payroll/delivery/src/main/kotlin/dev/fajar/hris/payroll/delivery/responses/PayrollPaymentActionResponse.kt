package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollPaymentActionResponse(
    val version: Long,
    val kind: String,
    val status: String,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
