package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveCarryExpiryAttempt(
    val jobId: UUID,
    val attempt: Int,
    val baseCompleted: Int,
    val startedAt: Instant,
    val reason: String,
)
