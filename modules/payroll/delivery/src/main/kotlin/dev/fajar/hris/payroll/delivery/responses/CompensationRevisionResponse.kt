package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class CompensationRevisionResponse(
    val compensation: CompensationResponse,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
