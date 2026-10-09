package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveBatchAttempt(
    val jobId: UUID,
    val number: Int,
    val baseCompleted: Int,
    val startedAt: Instant,
    val reason: String,
)
