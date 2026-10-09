package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollPeriodChangeResponse(
    val version: Long,
    val status: String,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
    val runId: UUID?,
)
