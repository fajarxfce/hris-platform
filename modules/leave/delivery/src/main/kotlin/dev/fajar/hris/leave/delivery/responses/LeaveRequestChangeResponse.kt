package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveRequestChangeResponse(
    val version: Long,
    val kind: String,
    val status: String,
    val cancellationApprovalId: UUID?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
